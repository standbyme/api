package standbyme.api.repository

import org.springframework.data.repository.CrudRepository
import standbyme.api.domain.File

interface FileRepository : CrudRepository<File, String>