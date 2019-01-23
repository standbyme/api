package standbyme.api

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import org.mockito.Mockito
import org.mockito.Mockito.*
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.client.discovery.simple.SimpleDiscoveryProperties.SimpleServiceInstance
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient

import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import java.net.URI


typealias Consumer<T> = (T) -> Unit

@RunWith(SpringRunner::class)
@WebFluxTest(APIController::class)
class APITests {
    @Autowired
    lateinit var webClient: WebTestClient

    @MockBean
    lateinit var mockWebClientBuilder: WebClient.Builder

    @MockBean
    lateinit var mockDiscoveryClient: DiscoveryClient

    lateinit var mockServer1: ClientAndServer
    lateinit var mockServer2: ClientAndServer

    @Before
    fun setUp() {
        Mockito.`when`(this.mockWebClientBuilder.build())
                .thenReturn(WebClient.builder().build())

        this.mockServer1 = startClientAndServer(1234)
        this.mockServer2 = startClientAndServer(1235)

        MockServerClient("localhost", 1234)
                .`when`(
                        request()
                )
                .respond(
                        response()
                                .withBody("Always Kid")
                )
    }

    @After
    fun shutdown() {
        this.mockServer1.stop()
        this.mockServer2.stop()
    }

    @Test
    fun notFound() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(1235).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })


        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .isNotFound

        verify(this.mockDiscoveryClient).getInstances("STORAGE")
    }

    @Test
    fun getTooManyResult() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(1234, 1234).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })


        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .is5xxServerError

        verify(this.mockDiscoveryClient).getInstances("STORAGE")
    }

    @Test
    fun successGet() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(1234, 1235).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })

        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .is2xxSuccessful
                .expectHeader()
                .contentLength(10)


        verify(this.mockDiscoveryClient).getInstances("STORAGE")
    }

}

