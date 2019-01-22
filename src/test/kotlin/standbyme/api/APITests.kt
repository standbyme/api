package standbyme.api

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.then
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient

@RunWith(SpringRunner::class)
@WebFluxTest(APIController::class)
class APITests {
    @Autowired
    private val webClient: WebTestClient? = null

    @MockBean
    private val webClientBuilder: WebClient.Builder? = null
    @MockBean
    private val discoveryClient: DiscoveryClient? = null

    @Test
    fun getSuccessfully() {
        this.webClient!!.get().uri("/objects/filename").exchange()
        then(this.discoveryClient).should()!!.getInstances("STORAGE")
    }

}

