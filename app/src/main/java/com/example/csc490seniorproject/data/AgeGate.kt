package com.example.csc490seniorproject.data

import java.time.LocalDate
import java.time.Period

object AgeGate {
    const val MIN_AGE = 13
    const val ADULT_AGE = 18

    fun ageOf(birth: LocalDate, today: LocalDate = LocalDate.now()) =
        Period.between(birth, today).years

    fun groupFor(age: Int) = if (age >= ADULT_AGE) "adult" else "minor"
}