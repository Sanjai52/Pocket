package `in`.marxen.pocket.data.local

import `in`.marxen.pocket.data.local.dao.InvestmentDao
import `in`.marxen.pocket.data.local.entity.InvestmentTypeEntity

/**
 * Single source of truth for the built-in investment types. Used by the fresh-install
 * seed, the 3->4 migration, the 4->5 migration (Gold), and the recovery after a backup
 * restore that predates a built-in type.
 */
object InvestmentDefaults {
    val TYPE_NAMES = listOf("Stocks", "IPO", "Mutual fund", "SIP", "Gold", "Other")

    fun types(): List<InvestmentTypeEntity> = TYPE_NAMES.mapIndexed { order, name ->
        InvestmentTypeEntity(name = name, sortOrder = order)
    }

    /**
     * Insert only the built-in types missing by name (case-insensitive), leaving every
     * existing row untouched. A pre-Gold type table therefore gains just Gold, while an
     * empty table (restore of a pre-v4 backup) is filled with the complete set.
     */
    suspend fun seedMissingDefaults(dao: InvestmentDao) {
        val existingNames = dao.allTypesSync()
            .map { it.name.trim().lowercase() }
            .toSet()
        types()
            .filterNot { it.name.lowercase() in existingNames }
            .forEach { dao.insertType(it) }
    }
}
