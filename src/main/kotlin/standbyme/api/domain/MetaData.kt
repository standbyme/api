package standbyme.api.domain

import javax.persistence.*
import javax.validation.constraints.NotBlank

@Entity
class MetaData {
    @Id
    @NotBlank
    @Column(updatable = false)
    val key: String?

    @NotBlank
    @JoinColumn(updatable = false)
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