package standbyme.api.repository

import org.springframework.data.repository.CrudRepository
import standbyme.api.domain.MetaData

interface MetaDataRepository : CrudRepository<MetaData, String>