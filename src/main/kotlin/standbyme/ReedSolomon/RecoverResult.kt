package standbyme.ReedSolomon

data class RecoverResult(val shards: Array<Shard>, val patch: Map<Int, Shard>)