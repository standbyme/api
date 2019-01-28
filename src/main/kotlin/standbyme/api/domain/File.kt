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
    val shardSize: Int?
    val fileSize: Int?

    constructor() {
        this.hash = null
        this.shardSize = null
        this.fileSize = null
    }

    constructor(hash: String, shardSize: Int, fileSize: Int) {
        this.hash = hash
        this.shardSize = shardSize
        this.fileSize = fileSize
    }
}