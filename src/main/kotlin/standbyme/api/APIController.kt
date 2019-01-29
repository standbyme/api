package standbyme.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import standbyme.api.domain.File
import standbyme.api.domain.MetaData
import standbyme.api.repository.FileRepository
import standbyme.api.repository.MetaDataRepository
import java.security.MessageDigest

import org.springframework.web.reactive.function.client.ClientResponse
import standbyme.ReedSolomon.Shard
import standbyme.ReedSolomon.decode
import standbyme.ReedSolomon.encode
import standbyme.ReedSolomon.recover
import java.nio.ByteBuffer

fun ByteArray.toHexString(): String = joinToString("") { String.format("%02x", it) }
fun Int.toByteArray(): ByteArray = ByteArray(1) { ByteBuffer.allocate(4).putInt(this).array()[3] }

@RestController
@RequestMapping("objects")
class APIController @Autowired constructor(private val webClientBuilder: WebClient.Builder, private val discoveryClient: DiscoveryClient, private val loadBalancer: LoadBalancerClient, private val metaDataRepository: MetaDataRepository, private val fileRepository: FileRepository) {

    val ok = ServerResponse.ok().build()
    val messageDigest = MessageDigest.getInstance("SHA-256")

    fun parseStorageResponse(byteArray: ByteArray, shardSize: Int): Map<Int, ByteArray> {
        val indexAndShardSize = 1 + shardSize
        val byteList = byteArray.toList()
        return byteList.chunked(indexAndShardSize) {
            val index = it.component1().toInt()
            val shard = it.drop(1).toByteArray()
            Pair(index, shard)
        }.toMap()
    }

    @GetMapping("{key:.+}", produces = arrayOf("application/octet-stream"))
    fun get(@PathVariable key: String): Mono<ByteArray> {
        val metaData = metaDataRepository.findByIdOrNull(key)
        return if (metaData == null) {
            throw NotFoundException(key)
        } else {
            val hash = metaData.file!!.hash!!
            val file = metaData.file
            val shardSize = file.shardSize!!
            val fileSize = file.fileSize!!

            val webClient = webClientBuilder.build()

            val instances = discoveryClient.getInstances("STORAGE")
            val urls = instances.map { """${it.uri}/objects/$hash""" }
            Flux.fromIterable(urls).flatMap {
                webClient.get().uri(it)
                        .retrieve()
                        .bodyToMono<ByteArray>()
                        .map { parseStorageResponse(it, shardSize) }
                        .onErrorResume { Mono.empty() }
            }.reduceWith({ arrayOfNulls<ByteArray>(6) }) { x, y ->
                y.forEach {
                    val index = it.key
                    val shard = it.value
                    x[index] = shard
                }
                x
            }.map {
                val recoverResult = recover(it, shardSize)
                val patch = recoverResult.patch
                patch.forEach { index, shard ->
                    putShard(index, shard, hash).subscribe()
                }
                val shards = recoverResult.shards
                decode(shards, fileSize)
            }
        }
    }

    fun computeHash(byteArray: ByteArray): String = messageDigest.digest(byteArray).toHexString()

    fun putShard(index: Int, shard: Shard, hash: String): Mono<ClientResponse> {
        val webClient = webClientBuilder.build()

        val instance = loadBalancer.choose("STORAGE")
        val url = """${instance.uri}/objects/$hash.$index"""

        return webClient
                .put().uri(url)
                .syncBody(index.toByteArray() + shard)
                .exchange()
    }

    @PutMapping("{key:.+}")
    fun put(@RequestBody dataMono: Mono<ByteArray>, @PathVariable key: String): Mono<ServerResponse> {
        return dataMono.flatMap { data ->
            val hash = computeHash(data)
            val isExisted = fileRepository.existsById(hash)
            when (isExisted) {
                true -> {
                    val file = fileRepository.findByIdOrNull(hash)!!
                    val metaData = MetaData(key, file)
                    metaDataRepository.save(metaData)
                    ok
                }
                false -> {

                    val encodeResult = encode(data)
                    val shardSize = encodeResult.shardSize
                    val shards = encodeResult.shards
                    shards.forEach {
                        assert(shardSize == it.size)
                    }
                    val fileSize = data.size

                    val file = File(hash, shardSize, fileSize)
                    val metaData = MetaData(key, file)
                    fileRepository.save(file)
                    metaDataRepository.save(metaData)
                    val shardFlux = Flux.fromArray(shards)
                    shardFlux.index().flatMap {
                        val index = it.t1
                        val shard = it.t2
                        putShard(index.toInt(), shard, hash)
                    }.subscribe()
                    ok
                }
            }
        }
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class NotFoundException(message: String) : Exception(message)