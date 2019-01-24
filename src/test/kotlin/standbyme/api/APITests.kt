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
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import java.net.URI

@RunWith(SpringRunner::class)
@WebFluxTest(APIController::class)
class APITests {
    @Autowired
    lateinit var webClient: WebTestClient

    @MockBean
    lateinit var mockWebClientBuilder: WebClient.Builder

    @MockBean
    lateinit var mockDiscoveryClient: DiscoveryClient

    lateinit var contentServer: ClientAndServer
    lateinit var notFoundServer: ClientAndServer
    var contentServerPort: Int = 0
    var notFoundServerPort: Int = 0

    @Before
    fun setUp() {
        Mockito.`when`(this.mockWebClientBuilder.build())
                .thenReturn(WebClient.builder().build())

        this.contentServer = startClientAndServer()
        this.notFoundServer = startClientAndServer()
        this.contentServerPort = this.contentServer.localPort
        this.notFoundServerPort = this.notFoundServer.localPort

        this.contentServer
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
        this.contentServer.stop()
        this.notFoundServer.stop()
    }

    @Test
    fun notFound() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(notFoundServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })


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
                .thenReturn(listOf(contentServerPort, contentServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })


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
                .thenReturn(listOf(contentServerPort, notFoundServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })

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

