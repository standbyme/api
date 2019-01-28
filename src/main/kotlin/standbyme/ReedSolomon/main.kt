package standbyme.ReedSolomon

import com.backblaze.erasure.ReedSolomon
import java.util.*

typealias Shard = ByteArray

fun ByteArray?.println() {
    println(Arrays.toString(this))
}

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

    return RecoverResult(shards, patch)
}

fun main(args: Array<String>) {
    val a = encode("ssadasd".toByteArray()).shards
    val haha = Array<Shard?>(6) {
        a[it]
    }
    haha.forEach { it!!.println() }
    println("------")
    haha[0] = null
    haha[2] = null

    haha.forEach { it.println() }

    val recoverResult = recover(haha, 2)
    val patch = recoverResult.patch
    val shards = recoverResult.shards
    println("------Patch")
    patch.forEach {
        println(it.key)
        it.value.println()
    }
    println("------")
    shards.forEach { it!!.println() }
}