package standbyme.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@RestController
@RequestMapping("objects")
class APIController @Autowired constructor(private val webClientBuilder: WebClient.Builder) {

    val webClient = webClientBuilder.baseUrl("http://localhost:8081/objects").build()

    @GetMapping("{filename:.+}", produces = arrayOf("application/octet-stream"))
    fun get(@PathVariable filename: String): Mono<ByteArray> {
        return webClient.get().uri("""/$filename""")
                .retrieve()
                .bodyToMono<ByteArray>()
                .onErrorResume { Mono.empty() }
    }
}