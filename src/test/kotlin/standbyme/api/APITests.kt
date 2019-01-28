package standbyme.api

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor

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
import org.mockserver.verify.VerificationTimes
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient
import standbyme.ReedSolomon.encode
import standbyme.api.domain.File
import standbyme.api.domain.MetaData
import standbyme.api.repository.FileRepository
import standbyme.api.repository.MetaDataRepository
import java.net.URI
import java.util.*

@RunWith(SpringRunner::class)
@WebFluxTest(APIController::class)
class APITests {
    @Autowired
    lateinit var webClient: WebTestClient

    @MockBean
    lateinit var mockWebClientBuilder: WebClient.Builder

    @MockBean
    lateinit var mockDiscoveryClient: DiscoveryClient

    @MockBean
    lateinit var mockloadBalancer: LoadBalancerClient

    @MockBean
    lateinit var mockMetaDataRepository: MetaDataRepository

    @MockBean
    lateinit var mockFileRepository: FileRepository

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
    fun notFoundWhenMissMetaData() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(notFoundServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })


        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .isNotFound

        this.notFoundServer
                .verify(
                        request()
                        , VerificationTimes.exactly(0)
                )

    }

    @Test
    fun notFoundWhenMissResponse() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(notFoundServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })
        val file = File("hash256", 2, 2)
        Mockito.`when`(this.mockMetaDataRepository.findById("filename"))
                .thenReturn(Optional.of(MetaData("filename", file)))

        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .isNotFound

        this.notFoundServer
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/objects/hash256")
                        , VerificationTimes.exactly(1)
                )

    }

    @Test
    fun getTooManyResult() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(contentServerPort, contentServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })

        val file = File("hash256", 2, 2)
        Mockito.`when`(this.mockMetaDataRepository.findById("filename"))
                .thenReturn(Optional.of(MetaData("filename", file)))

        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .is5xxServerError

        verify(this.mockDiscoveryClient).getInstances("STORAGE")
        this.contentServer
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/objects/hash256")
                        , VerificationTimes.exactly(2)
                )
    }

    @Test
    fun successGet() {
        Mockito.`when`(this.mockDiscoveryClient.getInstances("STORAGE"))
                .thenReturn(listOf(contentServerPort, notFoundServerPort).map { SimpleServiceInstance(URI("""http://localhost:$it""")) })

        val file = File("hash256", 2, 2)

        Mockito.`when`(this.mockMetaDataRepository.findById("filename"))
                .thenReturn(Optional.of(MetaData("filename", file)))

        this.webClient
                .get()
                .uri("/objects/filename")
                .exchange()
                .expectStatus()
                .is2xxSuccessful
                .expectHeader()
                .contentLength(10)

        verify(this.mockMetaDataRepository).findById("filename")
        this.contentServer
                .verify(
                        request()
                                .withMethod("GET")
                                .withPath("/objects/hash256")
                        , VerificationTimes.once()
                )
    }

    @Test
    fun successPutWhenNonExisted() {
        Mockito.`when`(this.mockloadBalancer.choose("STORAGE"))
                .thenReturn(SimpleServiceInstance(URI("""http://localhost:$contentServerPort""")))

        val metaDataArgument = ArgumentCaptor.forClass(MetaData::class.java)

        this.webClient
                .put()
                .uri("/objects/filename")
                .syncBody("Always Kid")
                .exchange()
                .expectStatus()
                .is2xxSuccessful

        verify(mockMetaDataRepository).save(metaDataArgument.capture())

        verify(this.mockloadBalancer, times(6)).choose("STORAGE")

        val metaData = metaDataArgument.value
        assert(metaData.key == "filename")
        val file = metaData.file!!
        assert(file.hash == "8dc8a7600512edca429c9cbba2a103ac7476cfe6dd55bf3f3ea5734711b56d9b")
        assert(file.shardSize == 3)
        assert(file.fileSize == 10)

        val shards = encode("Always Kid".toByteArray()).shards

        (0..5).forEach {
            this.contentServer
                    .verify(
                            request()
                                    .withPath("/objects/8dc8a7600512edca429c9cbba2a103ac7476cfe6dd55bf3f3ea5734711b56d9b.$it")
                                    .withMethod("PUT")
                                    .withBody(shards[it]), VerificationTimes.exactly(1)
                    )
        }
    }

    @Test
    fun successPutWhenExisted() {
        Mockito.`when`(this.mockloadBalancer.choose("STORAGE"))
                .thenReturn(SimpleServiceInstance(URI("""http://localhost:$contentServerPort""")))

        Mockito.`when`(this.mockFileRepository.existsById("8dc8a7600512edca429c9cbba2a103ac7476cfe6dd55bf3f3ea5734711b56d9b"))
                .thenReturn(true)

        Mockito.`when`(this.mockFileRepository.findById("8dc8a7600512edca429c9cbba2a103ac7476cfe6dd55bf3f3ea5734711b56d9b"))
                .thenReturn(Optional.of(File("8dc8a7600512edca429c9cbba2a103ac7476cfe6dd55bf3f3ea5734711b56d9b", 2, 2)))

        val metaDataArgument = ArgumentCaptor.forClass(MetaData::class.java)

        this.webClient
                .put()
                .uri("/objects/filename")
                .syncBody("Always Kid")
                .exchange()
                .expectStatus()
                .is2xxSuccessful

        verify(mockMetaDataRepository).save(metaDataArgument.capture())

        val metaData = metaDataArgument.value
        assert(metaData.key == "filename")
        val file = metaData.file!!
        assert(file.hash == "8dc8a7600512edca429c9cbba2a103ac7476cfe6dd55bf3f3ea5734711b56d9b")
        assert(file.shardSize == 2)
        assert(file.fileSize == 2)

        this.contentServer
                .verify(
                        request()
                        , VerificationTimes.exactly(0)
                )
    }

}

