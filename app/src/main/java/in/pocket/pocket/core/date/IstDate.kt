package `in`.marxen.pocket.core.date

import java.time.LocalDate
import java.time.ZoneId

fun pocketZoneId(): ZoneId = ZoneId.of("Asia/Kolkata")

fun asiaKolkataToday(): LocalDate = LocalDate.now(pocketZoneId())
