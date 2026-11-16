package com.sxdpz23.navisarthi.data

import com.sxdpz23.navisarthi.ui.theme.VehicleModel

data class ReferenceSource(val label: String, val url: String)
data class VehicleReferenceSpecs(
    val vehicle: VehicleModel,
    val rows: List<Pair<String, String>>,
    val sources: List<ReferenceSource>,
    val note: String = "Oil quantities are service refills. Specifications can vary by year and market; check your motorcycle's manual.",
)

/** Verified against the linked Royal Enfield publications, 17 November 2026.
 * No fallback: a new vehicle must supply its own references explicitly.
 */
fun vehicleReferenceSpecs(vehicle: VehicleModel): VehicleReferenceSpecs {
    fun manual(file: String, label: String = "Owner's manual (UK edition)") =
        ReferenceSource(label, "https://www.royalenfield.com/content/dam/open-pdf/$file.pdf")
    return when (vehicle) {
        VehicleModel.Meteor350 -> VehicleReferenceSpecs(vehicle, listOf(
            "Tyre pressure" to "Front 32 psi · rear 32 solo / 36 pillion",
            "Engine oil" to "15W50 API SL / JASO MA2 · semi-synthetic · 1.7 L",
            "Coolant" to "Not applicable · air-oil cooled",
            "Fork oil" to "Royal Enfield fork oil · 453 ml per leg",
            "Brake fluid" to "DOT 4 · front 87 / rear 54 ml",
            "Spark plug gap" to "0.7–0.8 mm",
            "Fuel tank" to "15 L",
            "Battery" to "12 V 8 Ah VRLA (India specification)",
        ), listOf(manual("royal-enfield-meteor-350-owners-manual-uk-english"),
            ReferenceSource("India specifications", "https://www.royalenfield.com/in/en/motorcycles/meteor/")))
        VehicleModel.Himalayan450 -> VehicleReferenceSpecs(vehicle, listOf(
            "Tyre pressure" to "Front & rear 32 psi · solo / pillion",
            "Engine oil" to "10W40 API SN / JASO MA2 · semi-synthetic · 2.1 L with filter",
            "Coolant" to "TOTAL COOLELF AUTO SUPRA −37°C",
            "Fork oil" to "SS-47G · right 589 / left 507 ml (±2.5 ml)",
            "Brake fluid" to "DOT 4 · front 90 / rear 80 ml (±5 ml)",
            "Fuel tank" to "17 L",
            "Battery" to "12 V 8 Ah VRLA",
        ), listOf(manual("royal-enfield-new-himalayan-450-owners-manual-uk-english"),
            manual("royal_enfield_himalayan_450_technical_specifications", "India manual / specifications")))
        VehicleModel.Guerrilla450 -> VehicleReferenceSpecs(vehicle, listOf(
            "Tyre pressure" to "Front 32 / rear 36 psi · solo / pillion",
            "Engine oil" to "10W40 API SN / JASO MA2 · semi-synthetic · 2.1 L with filter",
            "Coolant" to "TOTAL COOLELF AUTO SUPRA −37°C",
            "Fork oil" to "SS-8 · 540 ml per leg",
            "Brake fluid" to "DOT 4 · front 88.3 / rear 75.2 ml",
            "Fuel tank" to "11 L",
            "Battery" to "12 V 8 Ah",
        ), listOf(manual("royal-enfield-guerrilla-450-owners-manual-english", "Owner's manual (English)"),
            ReferenceSource("India specifications", "https://www.royalenfield.com/in/en/motorcycles/guerrilla-450/")))
        VehicleModel.Bear650 -> VehicleReferenceSpecs(vehicle, listOf(
            "Tyre pressure" to "Front 32 psi · rear 36 solo / 42 pillion",
            "Engine oil" to "10W50 API SL or higher / JASO MA2 · fully synthetic · 3.1 L",
            "Coolant" to "Not applicable · air-oil cooled",
            "Fork oil" to "SS-47G · right 546 / left 549 ml (±2.5 ml)",
            "Brake fluid" to "DOT 4 · front 50 / rear 100 ml",
            "Spark plug gap" to "0.7–0.8 mm",
            "Fuel tank" to "13.7 L",
            "Battery" to "12 V 12 Ah VRLA",
        ), listOf(manual("royal-enfield-bear-650-owners-manual-euro-5-uk-english"),
            ReferenceSource("India specifications", "https://www.royalenfield.com/in/en/motorcycles/bear-650/")))
        VehicleModel.Himalayan750 -> VehicleReferenceSpecs(vehicle, listOf(
            "Engine oil grade" to "10W50 API SL / JASO MA2 · fully synthetic",
            "Coolant" to "Not applicable · air-oil cooled",
            "Fuel tank" to "17.8 L",
        ), listOf(ReferenceSource("India specifications", "https://www.royalenfield.com/in/en/motorcycles/himalayan-750/")),
            "Only values published in the linked specifications are shown. Service fluid quantities, tyre pressures and battery rating are not yet verified for this model.")
    }
}
