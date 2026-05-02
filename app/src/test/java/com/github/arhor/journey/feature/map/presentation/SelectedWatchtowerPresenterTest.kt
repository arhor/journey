package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.domain.model.WatchtowerResourceCost
import com.github.arhor.journey.feature.map.WatchtowerSheetPhase
import io.kotest.matchers.shouldBe
import org.junit.Test

class SelectedWatchtowerPresenterTest {

    @Test
    fun `present should enable claim and map labels when dormant watchtower is in range and affordable`() {
        // Given
        val subject = SelectedWatchtowerPresenter()
        val resourceAmounts = mapOf(ResourceType.SCRAP.typeId to 5)
        val tower = watchtower(
            phase = WatchtowerPhase.DISCOVERED_DORMANT,
            claimCost = WatchtowerResourceCost(
                resourceTypeId = ResourceType.SCRAP.typeId,
                amount = 5,
            ),
            revealRadiusMeters = 100.4,
        )
        val contextualTower = subject.withInteractionContext(
            watchtower = tower,
            actorLocation = tower.location,
            resourceAmounts = resourceAmounts,
        )

        // When
        val actual = subject.present(
            watchtower = contextualTower,
            resourceAmounts = resourceAmounts,
        )

        // Then
        actual.phase shouldBe WatchtowerSheetPhase.DISCOVERED_DORMANT
        actual.revealRadiusMeters shouldBe 100
        actual.distanceMeters shouldBe 0
        actual.claimCostLabel shouldBe "5 Scrap"
        actual.canClaim shouldBe true
        actual.claimDisabledReason shouldBe null
    }

    @Test
    fun `present should enable upgrade and map next level labels when claimed watchtower is upgradeable`() {
        // Given
        val subject = SelectedWatchtowerPresenter()
        val resourceAmounts = mapOf(ResourceType.COMPONENTS.typeId to 10)
        val tower = watchtower(
            phase = WatchtowerPhase.CLAIMED,
            level = 2,
            revealRadiusMeters = 125.4,
            nextRevealRadiusMeters = 175.6,
            nextUpgradeCost = WatchtowerResourceCost(
                resourceTypeId = ResourceType.COMPONENTS.typeId,
                amount = 10,
            ),
        )
        val contextualTower = subject.withInteractionContext(
            watchtower = tower,
            actorLocation = tower.location,
            resourceAmounts = resourceAmounts,
        )

        // When
        val actual = subject.present(
            watchtower = contextualTower,
            resourceAmounts = resourceAmounts,
        )

        // Then
        actual.phase shouldBe WatchtowerSheetPhase.CLAIMED
        actual.level shouldBe 2
        actual.revealRadiusMeters shouldBe 125
        actual.nextRevealRadiusMeters shouldBe 176
        actual.upgradeCostLabel shouldBe "10 Components"
        actual.canUpgrade shouldBe true
        actual.upgradeDisabledReason shouldBe null
        actual.isAtMaxLevel shouldBe false
    }

    @Test
    fun `present should select disabled claim reason from resources range or missing location`() {
        // Given
        val subject = SelectedWatchtowerPresenter()
        val tower = watchtower(
            phase = WatchtowerPhase.DISCOVERED_DORMANT,
            claimCost = WatchtowerResourceCost(
                resourceTypeId = ResourceType.SCRAP.typeId,
                amount = 5,
            ),
        )
        val enoughResources = mapOf(ResourceType.SCRAP.typeId to 5)
        val missingResources = mapOf(ResourceType.SCRAP.typeId to 0)

        // When
        val unaffordable = subject.present(
            watchtower = subject.withInteractionContext(
                watchtower = tower,
                actorLocation = tower.location,
                resourceAmounts = missingResources,
            ),
            resourceAmounts = missingResources,
        )
        val outOfRange = subject.present(
            watchtower = subject.withInteractionContext(
                watchtower = tower,
                actorLocation = GeoPoint(lat = 0.01, lon = 0.0),
                resourceAmounts = enoughResources,
            ),
            resourceAmounts = enoughResources,
        )
        val unavailable = subject.present(
            watchtower = subject.withInteractionContext(
                watchtower = tower,
                actorLocation = null,
                resourceAmounts = enoughResources,
            ),
            resourceAmounts = enoughResources,
        )

        // Then
        unaffordable.canClaim shouldBe false
        unaffordable.claimDisabledReason shouldBe "Not enough Scrap."
        outOfRange.canClaim shouldBe false
        outOfRange.claimDisabledReason shouldBe "Move closer to interact."
        unavailable.canClaim shouldBe false
        unavailable.claimDisabledReason shouldBe "Current location is not available yet."
    }

    @Test
    fun `present should select disabled upgrade reason when claimed watchtower lacks resources`() {
        // Given
        val subject = SelectedWatchtowerPresenter()
        val tower = watchtower(
            phase = WatchtowerPhase.CLAIMED,
            nextUpgradeCost = WatchtowerResourceCost(
                resourceTypeId = ResourceType.COMPONENTS.typeId,
                amount = 10,
            ),
        )
        val resourceAmounts = mapOf(ResourceType.COMPONENTS.typeId to 0)

        // When
        val actual = subject.present(
            watchtower = subject.withInteractionContext(
                watchtower = tower,
                actorLocation = tower.location,
                resourceAmounts = resourceAmounts,
            ),
            resourceAmounts = resourceAmounts,
        )

        // Then
        actual.canUpgrade shouldBe false
        actual.upgradeDisabledReason shouldBe "Not enough Components."
    }

    @Test
    fun `present should mark claimed watchtower at max level when no upgrade cost exists`() {
        // Given
        val subject = SelectedWatchtowerPresenter()
        val tower = watchtower(
            phase = WatchtowerPhase.CLAIMED,
            level = 3,
            nextUpgradeCost = null,
        )

        // When
        val actual = subject.present(
            watchtower = subject.withInteractionContext(
                watchtower = tower,
                actorLocation = tower.location,
                resourceAmounts = emptyMap(),
            ),
            resourceAmounts = emptyMap(),
        )

        // Then
        actual.canUpgrade shouldBe false
        actual.upgradeDisabledReason shouldBe null
        actual.isAtMaxLevel shouldBe true
    }

    private fun watchtower(
        phase: WatchtowerPhase,
        level: Int? = null,
        revealRadiusMeters: Double? = null,
        nextRevealRadiusMeters: Double? = null,
        claimCost: WatchtowerResourceCost? = null,
        nextUpgradeCost: WatchtowerResourceCost? = null,
    ): Watchtower =
        Watchtower(
            id = "tower-1",
            name = "Watchtower 1",
            description = "Description 1",
            location = GeoPoint(lat = 0.0, lon = 0.0),
            interactionRadiusMeters = 25.0,
            phase = phase,
            level = level,
            revealRadiusMeters = revealRadiusMeters,
            claimCost = claimCost,
            nextUpgradeCost = nextUpgradeCost,
            nextRevealRadiusMeters = nextRevealRadiusMeters,
            canClaim = false,
            canUpgrade = false,
            distanceMeters = null,
        )
}
