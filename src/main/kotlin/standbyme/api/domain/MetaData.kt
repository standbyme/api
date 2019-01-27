package standbyme.api.domain

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.validation.constraints.NotBlank

@Entity
class MetaData {
    @Id
    @NotBlank
    @Column(updatable = false)
    val key: String?

    @NotBlank
    @Column(updatable = false)
    @ManyToOne
    val file: File?

    constructor() {
        this.key = null
        this.file = null
    }

    constructor(key: String, file: File) {
        this.key = key
        this.file = file
    }
}