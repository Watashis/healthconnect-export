package com.healthconnect.export.data

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Health Connect Int constant → human-readable string mapper functions.
 *
 * Each mapper is tested for:
 * - null input → null
 * - Every known Int value → expected string
 * - Unknown Int value → "Other (value)"
 *
 * Mappers covered:
 * - bodyPositionToString (5 known + unknown + null)
 * - specimenSourceToString (6 known + unknown + null)
 * - mealTypeToString (5 known + unknown + null)
 * - sleepStageToString (6 known + unknown + null)
 * - measurementLocationToString (12 known + unknown + null)
 * - menstruationFlowToString (4 known + unknown + null)
 * - nutritionMealTypeToString (5 known + unknown + null)
 * - exerciseTypeToString (79 known + unknown + null)
 */
class HumanReadableMapperTest {

    // =============================================
    // bodyPositionToString
    // =============================================

    @Test
    fun `bodyPositionToString null returns null`() {
        assertNull(bodyPositionToString(null))
    }

    @Test
    fun `bodyPositionToString known values`() {
        assertEquals("Unknown", bodyPositionToString(0))
        assertEquals("Standing", bodyPositionToString(1))
        assertEquals("Sitting", bodyPositionToString(2))
        assertEquals("Lying down", bodyPositionToString(3))
        assertEquals("Reclining", bodyPositionToString(4))
    }

    @Test
    fun `bodyPositionToString unknown value returns Other`() {
        assertEquals("Other (5)", bodyPositionToString(5))
        assertEquals("Other (-1)", bodyPositionToString(-1))
        assertEquals("Other (99)", bodyPositionToString(99))
    }



    // =============================================
    // specimenSourceToString
    // =============================================

    @Test
    fun `specimenSourceToString null returns null`() {
        assertNull(specimenSourceToString(null))
    }

    @Test
    fun `specimenSourceToString known values`() {
        assertEquals("Unknown", specimenSourceToString(0))
        assertEquals("Interstitial fluid", specimenSourceToString(1))
        assertEquals("Capillary blood", specimenSourceToString(2))
        assertEquals("Plasma", specimenSourceToString(3))
        assertEquals("Serum", specimenSourceToString(4))
        assertEquals("Whole blood", specimenSourceToString(5))
    }

    @Test
    fun `specimenSourceToString unknown value returns Other`() {
        assertEquals("Other (6)", specimenSourceToString(6))
        assertEquals("Other (100)", specimenSourceToString(100))
    }

    // =============================================
    // mealTypeToString (blood glucose)
    // =============================================

    @Test
    fun `mealTypeToString null returns null`() {
        assertNull(mealTypeToString(null))
    }

    @Test
    fun `mealTypeToString known values`() {
        assertEquals("Unknown", mealTypeToString(0))
        assertEquals("Fasting", mealTypeToString(1))
        assertEquals("Before meal", mealTypeToString(2))
        assertEquals("After meal", mealTypeToString(3))
        assertEquals("General", mealTypeToString(4))
    }

    @Test
    fun `mealTypeToString unknown value returns Other`() {
        assertEquals("Other (5)", mealTypeToString(5))
        assertEquals("Other (-5)", mealTypeToString(-5))
    }

    // =============================================
    // sleepStageToString
    // =============================================

    @Test
    fun `sleepStageToString null returns null`() {
        assertNull(sleepStageToString(null))
    }

    @Test
    fun `sleepStageToString known values`() {
        assertEquals("Unknown", sleepStageToString(0))
        assertEquals("Awake", sleepStageToString(1))
        assertEquals("Deep sleep", sleepStageToString(2))
        assertEquals("Light sleep", sleepStageToString(3))
        assertEquals("REM sleep", sleepStageToString(4))
        assertEquals("Out of bed", sleepStageToString(5))
    }

    @Test
    fun `sleepStageToString unknown value returns Other`() {
        assertEquals("Other (6)", sleepStageToString(6))
        assertEquals("Other (42)", sleepStageToString(42))
    }

    // =============================================
    // measurementLocationToString
    // =============================================

    @Test
    fun `measurementLocationToString null returns null`() {
        assertNull(measurementLocationToString(null))
    }

    @Test
    fun `measurementLocationToString known values`() {
        assertEquals("Unknown", measurementLocationToString(0))
        assertEquals("Axillary", measurementLocationToString(1))
        assertEquals("Body", measurementLocationToString(2))
        assertEquals("Ear", measurementLocationToString(3))
        assertEquals("Finger", measurementLocationToString(4))
        assertEquals("Forehead", measurementLocationToString(5))
        assertEquals("Mouth", measurementLocationToString(6))
        assertEquals("Rectal", measurementLocationToString(7))
        assertEquals("Toe", measurementLocationToString(8))
        assertEquals("Tympanic", measurementLocationToString(9))
        assertEquals("Wrist", measurementLocationToString(10))
        assertEquals("Temporal artery", measurementLocationToString(11))
    }

    @Test
    fun `measurementLocationToString unknown value returns Other`() {
        assertEquals("Other (12)", measurementLocationToString(12))
        assertEquals("Other (50)", measurementLocationToString(50))
    }

    // =============================================
    // menstruationFlowToString
    // =============================================

    @Test
    fun `menstruationFlowToString null returns null`() {
        assertNull(menstruationFlowToString(null))
    }

    @Test
    fun `menstruationFlowToString known values`() {
        assertEquals("Unknown", menstruationFlowToString(0))
        assertEquals("Light", menstruationFlowToString(1))
        assertEquals("Medium", menstruationFlowToString(2))
        assertEquals("Heavy", menstruationFlowToString(3))
    }

    @Test
    fun `menstruationFlowToString unknown value returns Other`() {
        assertEquals("Other (4)", menstruationFlowToString(4))
        assertEquals("Other (10)", menstruationFlowToString(10))
    }

    // =============================================
    // nutritionMealTypeToString
    // =============================================

    @Test
    fun `nutritionMealTypeToString null returns null`() {
        assertNull(nutritionMealTypeToString(null))
    }

    @Test
    fun `nutritionMealTypeToString known values`() {
        assertEquals("Unknown", nutritionMealTypeToString(0))
        assertEquals("Breakfast", nutritionMealTypeToString(1))
        assertEquals("Lunch", nutritionMealTypeToString(2))
        assertEquals("Dinner", nutritionMealTypeToString(3))
        assertEquals("Snack", nutritionMealTypeToString(4))
    }

    @Test
    fun `nutritionMealTypeToString unknown value returns Other`() {
        assertEquals("Other (5)", nutritionMealTypeToString(5))
        assertEquals("Other (99)", nutritionMealTypeToString(99))
    }

    // =============================================
    // exerciseTypeToString — biggest mapper (79 known)
    // =============================================

    @Test
    fun `exerciseTypeToString null returns null`() {
        assertNull(exerciseTypeToString(null))
    }

    @Test
    fun `exerciseTypeToString first 20 known values`() {
        assertEquals("Other workout", exerciseTypeToString(0))
        assertEquals("Back extension", exerciseTypeToString(1))
        assertEquals("Badminton", exerciseTypeToString(2))
        assertEquals("Baseball", exerciseTypeToString(3))
        assertEquals("Basketball", exerciseTypeToString(4))
        assertEquals("Cycling", exerciseTypeToString(5))
        assertEquals("Stationary cycling", exerciseTypeToString(6))
        assertEquals("Boot camp", exerciseTypeToString(7))
        assertEquals("Boxing", exerciseTypeToString(8))
        assertEquals("Burpee", exerciseTypeToString(9))
        assertEquals("Calisthenics", exerciseTypeToString(10))
        assertEquals("Cricket", exerciseTypeToString(11))
        assertEquals("CrossFit", exerciseTypeToString(12))
        assertEquals("Crunch", exerciseTypeToString(13))
        assertEquals("Dancing", exerciseTypeToString(14))
        assertEquals("Deadlift", exerciseTypeToString(15))
        assertEquals("Fencing", exerciseTypeToString(16))
        assertEquals("American football", exerciseTypeToString(17))
        assertEquals("Australian football", exerciseTypeToString(18))
        assertEquals("Soccer", exerciseTypeToString(19))
    }

    @Test
    fun `exerciseTypeToString middle 20 known values`() {
        assertEquals("Frisbee", exerciseTypeToString(20))
        assertEquals("General workout", exerciseTypeToString(21))
        assertEquals("Golf", exerciseTypeToString(22))
        assertEquals("Breathing exercises", exerciseTypeToString(23))
        assertEquals("Gymnastics", exerciseTypeToString(24))
        assertEquals("Handball", exerciseTypeToString(25))
        assertEquals("HIIT", exerciseTypeToString(26))
        assertEquals("Hiking", exerciseTypeToString(27))
        assertEquals("Hockey", exerciseTypeToString(28))
        assertEquals("Figure skating", exerciseTypeToString(29))
        assertEquals("Jumping jack", exerciseTypeToString(30))
        assertEquals("Jump rope", exerciseTypeToString(31))
        assertEquals("Kayaking", exerciseTypeToString(32))
        assertEquals("Kettlebell", exerciseTypeToString(33))
        assertEquals("Kickboxing", exerciseTypeToString(34))
        assertEquals("Kitesurfing", exerciseTypeToString(35))
        assertEquals("Martial arts", exerciseTypeToString(36))
        assertEquals("Meditation", exerciseTypeToString(37))
        assertEquals("MMA", exerciseTypeToString(38))
        assertEquals("P90X", exerciseTypeToString(39))
    }

    @Test
    fun `exerciseTypeToString middle 20 known values part 2`() {
        assertEquals("Pilates", exerciseTypeToString(40))
        assertEquals("Plank", exerciseTypeToString(41))
        assertEquals("Racquetball", exerciseTypeToString(42))
        assertEquals("Rock climbing", exerciseTypeToString(43))
        assertEquals("Roller skating", exerciseTypeToString(44))
        assertEquals("Rowing", exerciseTypeToString(45))
        assertEquals("Rowing machine", exerciseTypeToString(46))
        assertEquals("Rugby", exerciseTypeToString(47))
        assertEquals("Running", exerciseTypeToString(48))
        assertEquals("Treadmill", exerciseTypeToString(49))
        assertEquals("Sailing", exerciseTypeToString(50))
        assertEquals("Scuba diving", exerciseTypeToString(51))
        assertEquals("Ice skating", exerciseTypeToString(52))
        assertEquals("Skiing", exerciseTypeToString(53))
        assertEquals("Cross-country skiing", exerciseTypeToString(54))
        assertEquals("Downhill skiing", exerciseTypeToString(55))
        assertEquals("Kite skiing", exerciseTypeToString(56))
        assertEquals("Roller skiing", exerciseTypeToString(57))
        assertEquals("Sledding", exerciseTypeToString(58))
        assertEquals("Sleep", exerciseTypeToString(59))
    }

    @Test
    fun `exerciseTypeToString last known values`() {
        assertEquals("Snowboarding", exerciseTypeToString(60))
        assertEquals("Snowshoeing", exerciseTypeToString(61))
        assertEquals("Squash", exerciseTypeToString(62))
        assertEquals("Stair climbing", exerciseTypeToString(63))
        assertEquals("Stair stepper", exerciseTypeToString(64))
        assertEquals("Strength training", exerciseTypeToString(65))
        assertEquals("Stretching", exerciseTypeToString(66))
        assertEquals("Surfing", exerciseTypeToString(67))
        assertEquals("Swimming (open water)", exerciseTypeToString(68))
        assertEquals("Swimming (pool)", exerciseTypeToString(69))
        assertEquals("Table tennis", exerciseTypeToString(70))
        assertEquals("Tennis", exerciseTypeToString(71))
        assertEquals("Upper body", exerciseTypeToString(72))
        assertEquals("Volleyball", exerciseTypeToString(73))
        assertEquals("Walking", exerciseTypeToString(74))
        assertEquals("Water polo", exerciseTypeToString(75))
        assertEquals("Weightlifting", exerciseTypeToString(76))
        assertEquals("Wheelchair", exerciseTypeToString(77))
        assertEquals("Yoga", exerciseTypeToString(78))
    }

    @Test
    fun `exerciseTypeToString unknown value returns Other`() {
        assertEquals("Other (79)", exerciseTypeToString(79))
        assertEquals("Other (1000)", exerciseTypeToString(1000))
        assertEquals("Other (-1)", exerciseTypeToString(-1))
    }


}
