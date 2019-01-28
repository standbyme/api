package standbyme.ReedSolomon

import com.backblaze.erasure.ReedSolomon

typealias Shard = ByteArray

const val DATA_SHARDS = 4
const val PARITY_SHARDS = 2
const val TOTAL_SHARDS = DATA_SHARDS + PARITY_SHARDS

fun encode(data: ByteArray): EncodeResult {
    val dataSize = data.size
    val shardSize = (dataSize + DATA_SHARDS - 1) / DATA_SHARDS
    val dataShardsSize = shardSize * DATA_SHARDS

    val paddingData = data.copyOf(dataShardsSize)

    val shards = Array(TOTAL_SHARDS) { ByteArray(shardSize) }
    (0 until DATA_SHARDS).forEach { i ->
        System.arraycopy(paddingData, i * shardSize, shards[i], 0, shardSize)
    }
    val reedSolomon = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS)
    reedSolomon.encodeParity(shards, 0, shardSize)
    return EncodeResult(shards, shardSize)
}

fun recover(shards: Array<Shard?>, shardSize: Int): RecoverResult {
    val missShardIndexSet = mutableSetOf<Int>()

    val shardPresent = BooleanArray(TOTAL_SHARDS) {
        if (shards[it] == null) {
            missShardIndexSet.add(it)
            shards[it] = ByteArray(shardSize)
            false
        } else {
            true
        }
    }

    val reedSolomon = ReedSolomon.create(DATA_SHARDS, PARITY_SHARDS)
    reedSolomon.decodeMissing(shards, shardPresent, 0, shardSize)

    val patch = mutableMapOf<Int, Shard>()
    missShardIndexSet.forEach { patch[it] = shards[it]!! }

    return RecoverResult(shards.map { it!! }.toTypedArray(), patch)
}

fun decode(shards: Array<Shard>, fileSize: Int): ByteArray {
    return shards.sliceArray(0..3).flatMap { it.toList() }.take(fileSize).toByteArray()
}