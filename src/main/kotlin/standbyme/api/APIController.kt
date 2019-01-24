package standbyme.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("objects")
class APIController @Autowired constructor(private val webClientBuilder: WebClient.Builder, private val discoveryClient: DiscoveryClient, private val loadBalancer: LoadBalancerClient) {

    val ok = ServerResponse.ok().build()

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
        }.single().onErrorResume {
            when (it) {
                is NoSuchElementException -> Mono.error(NotFoundException(filename))
                else -> Mono.error(it)
            }
        }
    }

    @PutMapping("{filename:.+}")
    fun put(@RequestBody data: Mono<String>, @PathVariable filename: String): Mono<ServerResponse> {
        val webClient = webClientBuilder.build()
        val instance = loadBalancer.choose("STORAGE")
        val url = """${instance.uri}/objects/$filename"""

        return webClient
                .put().uri(url)
                .body(data, String::class.java)
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

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class NotFoundException(message: String) : Exception(message)