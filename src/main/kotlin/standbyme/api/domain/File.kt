package standbyme.api.domain

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.validation.constraints.NotBlank

@Entity
class File {
    @Id
    @NotBlank
    @Column(updatable = false)
    val hash: String?

    constructor() {
        this.hash = null
    }

    constructor(hash: String) {
        this.hash = hash
    }
}