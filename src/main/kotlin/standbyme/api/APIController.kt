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

fun ByteArray.toHexString(): String = joinToString("") { String.format("%02x", it) }

@RestController
@RequestMapping("objects")
class APIController @Autowired constructor(private val webClientBuilder: WebClient.Builder, private val discoveryClient: DiscoveryClient, private val loadBalancer: LoadBalancerClient, private val metaDataRepository: MetaDataRepository, private val fileRepository: FileRepository) {

    val ok = ServerResponse.ok().build()
    val messageDigest = MessageDigest.getInstance("SHA-256")

    @GetMapping("{key:.+}", produces = arrayOf("application/octet-stream"))
    fun get(@PathVariable key: String): Mono<ByteArray> {
        val metaData = metaDataRepository.findByIdOrNull(key)
        return if (metaData == null) {
            throw NotFoundException(key)
        } else {
            val hash = metaData.file!!.hash!!
            val webClient = webClientBuilder.build()
            val instances = discoveryClient.getInstances("STORAGE")
            val urls = instances.map { """${it.uri}/objects/$hash""" }
            Flux.fromIterable(urls).flatMap {
                webClient.get().uri(it)
                        .retrieve()
                        .bodyToMono<ByteArray>()
                        .onErrorResume { Mono.empty() }
            }.single().onErrorResume {
                when (it) {
                    is NoSuchElementException -> Mono.error(NotFoundException(key))
                    else -> Mono.error(it)
                }
            }
        }
    }

    @PutMapping("{key:.+}")
    fun put(@RequestBody data: Mono<ByteArray>, @PathVariable key: String): Mono<ServerResponse> {
        val webClient = webClientBuilder.build()
        val instance = loadBalancer.choose("STORAGE")
        val hashMono = data.map { messageDigest.digest(it).toHexString() }
        val isExistedMono = hashMono.map { fileRepository.existsById(it) }

        return isExistedMono.flatMap { isExisted ->
            when (isExisted) {
                true -> {
                    val fileMono = hashMono.map { hash -> fileRepository.findByIdOrNull(hash)!! }
                    val metaDataMono = fileMono.map { file -> MetaData(key, file) }
                    metaDataMono.flatMap { metaData ->
                        metaDataRepository.save(metaData)
                        ok
                    }
                }
                false -> {
                    val urlMono = hashMono.map { """${instance.uri}/objects/$it""" }
                    val fileMono = hashMono.map { hash -> File(hash) }
                    val metaDataMono = fileMono.map { file -> MetaData(key, file) }
                    metaDataMono.flatMap { metaData ->
                        metaDataRepository.save(metaData)
                        urlMono.flatMap { url ->
                            webClient
                                    .put().uri(url)
                                    .body(data, ByteArray::class.java)
                                    .exchange()
                                    .flatMap {
                                        val statusCode = it.statusCode()
                                        when {
                                            statusCode.is2xxSuccessful -> ok
                                            else -> Mono.error(Exception("kid"))
                                        }
                                    }
                        }
                    }
                }
            }
        }
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class NotFoundException(message: String) : Exception(message)