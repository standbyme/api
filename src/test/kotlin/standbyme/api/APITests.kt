package standbyme.api

import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.BDDMockito.then
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.mockito.BDDMockito.given
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.web.reactive.function.client.WebClient

@RunWith(SpringRunner::class)
@AutoConfigureMockMvc
@SpringBootTest
class APITests {
    @Autowired
    private val mvc: MockMvc? = null

    @MockBean
    private val webClientBuilder: WebClient.Builder? = null
    @MockBean
    private val discoveryClient: DiscoveryClient? = null

    @Test
    fun getSuccessfully() {
        this.mvc!!.perform(get("/objects/filename"))
        then(this.discoveryClient).should()!!.getInstances("STORAGE")
    }

}

