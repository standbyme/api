package standbyme.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("objects")
class APIController @Autowired constructor(private val webClientBuilder: WebClient.Builder, private val discoveryClient: DiscoveryClient) {

    @GetMapping("{filename:.+}", produces = arrayOf("application/octet-stream"))
    fun get(@PathVariable filename: String): Mono<ByteArray> {
        val webClient = webClientBuilder.build()
        val instances = discoveryClient.getInstances("STORAGE")
        val urls = instances.map { """${it.uri}/objects/$filename""" }
        return Flux.fromIterable(urls).flatMap {
            webClient.get().uri(it)
                    .retrieve()
                    .bodyToMono<ByteArray>()
                    .onErrorResume { Mono.empty() }
        }.single().onErrorResume { Mono.error(NotFoundException(filename)) }
    }
}

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class NotFoundException(message: String) : Exception(message)