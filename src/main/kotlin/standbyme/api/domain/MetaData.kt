package standbyme.api.domain

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.validation.constraints.NotBlank

@Entity
class MetaData {
    @Id
    @NotBlank
    @Column(updatable = false)
    val key: String?

    @NotBlank
    @Column(updatable = false)
    val hash: String?

    constructor() {
        this.key = null
        this.hash = null
    }

    constructor(key: String, hash: String) {
        this.key = key
        this.hash = hash
    }
}