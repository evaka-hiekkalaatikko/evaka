// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.invoicing.service

import com.fasterxml.jackson.databind.ObjectMapper
import fi.espoo.evaka.assistanceneed.AssistanceNeedCapacityFactor
import fi.espoo.evaka.assistanceneed.getCapacityFactorsByChild
import fi.espoo.evaka.invoicing.data.deleteValueDecisions
import fi.espoo.evaka.invoicing.data.findValueDecisionsForChild
import fi.espoo.evaka.invoicing.data.getFeeAlterationsFrom
import fi.espoo.evaka.invoicing.data.getFeeThresholds
import fi.espoo.evaka.invoicing.data.getIncomesFrom
import fi.espoo.evaka.invoicing.data.lockValueDecisionsForChild
import fi.espoo.evaka.invoicing.data.upsertValueDecisions
import fi.espoo.evaka.invoicing.domain.FeeAlteration
import fi.espoo.evaka.invoicing.domain.FeeThresholds
import fi.espoo.evaka.invoicing.domain.FridgeFamily
import fi.espoo.evaka.invoicing.domain.Income
import fi.espoo.evaka.invoicing.domain.PersonData
import fi.espoo.evaka.invoicing.domain.PlacementWithServiceNeed
import fi.espoo.evaka.invoicing.domain.UnitData
import fi.espoo.evaka.invoicing.domain.VoucherValue
import fi.espoo.evaka.invoicing.domain.VoucherValueDecision
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionPlacement
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionServiceNeed
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionStatus
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionType
import fi.espoo.evaka.invoicing.domain.calculateBaseFee
import fi.espoo.evaka.invoicing.domain.calculateFeeBeforeFeeAlterations
import fi.espoo.evaka.invoicing.domain.calculateVoucherValue
import fi.espoo.evaka.invoicing.domain.decisionContentsAreEqual
import fi.espoo.evaka.invoicing.domain.firstOfMonthAfterThirdBirthday
import fi.espoo.evaka.invoicing.domain.getAgeCoefficient
import fi.espoo.evaka.invoicing.domain.toFeeAlterationsWithEffects
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.VoucherValueDecisionId
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.domain.DateRange
import fi.espoo.evaka.shared.domain.asDistinctPeriods
import fi.espoo.evaka.shared.domain.mergePeriods
import org.jdbi.v3.core.kotlin.mapTo
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal fun Database.Transaction.handleValueDecisionChanges(
    capacityFactorEnabled: Boolean,
    objectMapper: ObjectMapper,
    incomeTypesProvider: IncomeTypesProvider,
    from: LocalDate,
    child: PersonData.WithDateOfBirth,
    families: List<FridgeFamily>
) {
    val children = families.flatMap { it.children }
    val fridgeSiblings = families.flatMap { it.fridgeSiblings }

    val prices = getFeeThresholds(from)
    val voucherValues = getVoucherValues(from)
    val adults = families.flatMap { listOfNotNull(it.headOfFamily.id, it.partner?.id) }
    val incomes = getIncomesFrom(objectMapper, incomeTypesProvider, adults, from)
    val capacityFactors = if (capacityFactorEnabled) getCapacityFactorsByChild(child.id) else listOf()
    val feeAlterations = getFeeAlterationsFrom(listOf(child.id), from) + addECHAFeeAlterations(listOf(child), incomes)

    val placements = getPaidPlacements(from, children + fridgeSiblings).toMap()
    val serviceVoucherUnits = getServiceVoucherUnits()

    val newDrafts =
        generateNewValueDecisions(
            from,
            child,
            families,
            placements,
            prices,
            voucherValues,
            incomes,
            capacityFactors,
            feeAlterations,
            serviceVoucherUnits
        )

    lockValueDecisionsForChild(child.id)

    val existingDrafts =
        findValueDecisionsForChild(child.id, null, listOf(VoucherValueDecisionStatus.DRAFT))
    val activeDecisions = findValueDecisionsForChild(
        child.id,
        null,
        listOf(
            VoucherValueDecisionStatus.WAITING_FOR_SENDING,
            VoucherValueDecisionStatus.WAITING_FOR_MANUAL_SENDING,
            VoucherValueDecisionStatus.SENT
        )
    )

    val updatedDecisions = updateExistingDecisions(from, newDrafts, existingDrafts, activeDecisions)
    deleteValueDecisions(existingDrafts.map { it.id })
    upsertValueDecisions(updatedDecisions)
}

private fun generateNewValueDecisions(
    from: LocalDate,
    voucherChild: PersonData.WithDateOfBirth,
    families: List<FridgeFamily>,
    allPlacements: Map<PersonData.WithDateOfBirth, List<Pair<DateRange, PlacementWithServiceNeed>>>,
    prices: List<FeeThresholds>,
    voucherValues: List<VoucherValue>,
    incomes: List<Income>,
    capacityFactors: List<AssistanceNeedCapacityFactor>,
    feeAlterations: List<FeeAlteration>,
    serviceVoucherUnits: List<DaycareId>
): List<VoucherValueDecision> {
    val periods = families.map { it.period } +
        incomes.map { DateRange(it.validFrom, it.validTo) } +
        capacityFactors.map { it.dateRange } +
        prices.map { it.validDuring } +
        allPlacements.flatMap { (child, placements) ->
            placements.map { it.first } + listOf(
                DateRange(child.dateOfBirth, firstOfMonthAfterThirdBirthday(child.dateOfBirth).minusDays(1)),
                DateRange(firstOfMonthAfterThirdBirthday(child.dateOfBirth), null)
            )
        } +
        feeAlterations.map { DateRange(it.validFrom, it.validTo) }

    return asDistinctPeriods(periods, DateRange(from, null))
        .mapNotNull { period ->
            val family = families.find { it.period.overlaps(period) }
                ?: return@mapNotNull null

            val price = prices.find { it.validDuring.contains(period) }
                ?: error("Missing price for period ${period.start} - ${period.end}, cannot generate voucher value decision")

            val voucherValue = voucherValues.find { it.validity.contains(period) }
                ?: error("Missing voucher value for period ${period.start} - ${period.end}, cannot generate voucher value decision")

            val income = incomes
                .find { family.headOfFamily.id == it.personId && DateRange(it.validFrom, it.validTo).contains(period) }
                ?.toDecisionIncome()

            val partnerIncome = family.partner?.let { partner ->
                incomes
                    .find {
                        partner.id == it.personId && DateRange(
                            it.validFrom,
                            it.validTo
                        ).contains(period)
                    }
                    ?.toDecisionIncome()
            }

            val capacityFactor =
                capacityFactors.find { it.dateRange.contains(period) }?.capacityFactor
                    ?: BigDecimal("1.00")

            val validPlacements = (family.children + family.fridgeSiblings)
                .mapNotNull { child ->
                    val childPlacements = allPlacements[child] ?: listOf()
                    val validPlacement = childPlacements.find { (dateRange, _) ->
                        dateRange.contains(period)
                    }
                    validPlacement?.let { (_, placement) -> child to placement }
                }

            val voucherChildPlacement = validPlacements.find { (child, _) -> child == voucherChild }?.second

            voucherChildPlacement
                // Skip the placement if it's not into a service voucher unit
                ?.takeIf { placement -> serviceVoucherUnits.any { unit -> unit == placement.unitId } }
                ?.let { placement ->
                    val familyIncomes = family.partner?.let { listOf(income, partnerIncome) } ?: listOf(income)
                    val baseCoPayment = calculateBaseFee(price, family.getSize(), familyIncomes)

                    val siblingIndex = validPlacements
                        .sortedByDescending { (child, _) -> child.dateOfBirth }
                        .indexOfFirst { (child, _) -> child == voucherChild }

                    val siblingDiscountMultiplier = price.siblingDiscountMultiplier(siblingIndex + 1)
                    val coPaymentBeforeAlterations =
                        calculateFeeBeforeFeeAlterations(baseCoPayment, placement.serviceNeed.feeCoefficient, siblingDiscountMultiplier, price.minFee)
                    val relevantFeeAlterations = feeAlterations.filter {
                        DateRange(it.validFrom, it.validTo).contains(period)
                    }
                    val feeAlterationsWithEffects =
                        toFeeAlterationsWithEffects(coPaymentBeforeAlterations, relevantFeeAlterations)
                    val finalCoPayment = coPaymentBeforeAlterations + feeAlterationsWithEffects.sumOf { it.effect }

                    val ageCoefficient = getAgeCoefficient(period, voucherChild.dateOfBirth, voucherValue)
                    val value = calculateVoucherValue(voucherValue, ageCoefficient, capacityFactor, placement.serviceNeed.voucherValueCoefficient)

                    period to VoucherValueDecision(
                        id = VoucherValueDecisionId(UUID.randomUUID()),
                        status = VoucherValueDecisionStatus.DRAFT,
                        decisionType = VoucherValueDecisionType.NORMAL,
                        headOfFamily = family.headOfFamily,
                        partner = family.partner,
                        headOfFamilyIncome = income,
                        partnerIncome = partnerIncome,
                        familySize = family.getSize(),
                        feeThresholds = price.getFeeDecisionThresholds(family.getSize()),
                        validFrom = period.start,
                        validTo = period.end,
                        child = voucherChild,
                        placement = VoucherValueDecisionPlacement(UnitData.JustId(placement.unitId), placement.type),
                        serviceNeed = VoucherValueDecisionServiceNeed(
                            placement.serviceNeed.feeCoefficient,
                            placement.serviceNeed.voucherValueCoefficient,
                            placement.serviceNeed.feeDescriptionFi,
                            placement.serviceNeed.feeDescriptionSv,
                            placement.serviceNeed.voucherValueDescriptionFi,
                            placement.serviceNeed.voucherValueDescriptionSv
                        ),
                        baseCoPayment = baseCoPayment,
                        siblingDiscount = price.siblingDiscountPercent(siblingIndex + 1),
                        coPayment = coPaymentBeforeAlterations,
                        feeAlterations = toFeeAlterationsWithEffects(
                            coPaymentBeforeAlterations,
                            relevantFeeAlterations
                        ),
                        finalCoPayment = finalCoPayment,
                        baseValue = voucherValue.baseValue,
                        ageCoefficient = ageCoefficient,
                        capacityFactor = capacityFactor,
                        voucherValue = value
                    )
                }
        }
        .let { mergePeriods(it, ::decisionContentsAreEqual) }
        .map { (period, decision) -> decision.withValidity(period) }
}

private fun Database.Read.getServiceVoucherUnits(): List<DaycareId> {
    // language=sql
    return createQuery("SELECT id FROM daycare WHERE provider_type = 'PRIVATE_SERVICE_VOUCHER' AND NOT invoiced_by_municipality")
        .mapTo<DaycareId>()
        .toList()
}

private fun Database.Read.getVoucherValues(from: LocalDate): List<VoucherValue> {
    // language=sql
    val sql = "SELECT * FROM voucher_value WHERE validity && daterange(:from, null, '[]')"

    return createQuery(sql)
        .bind("from", from)
        .mapTo<VoucherValue>()
        .toList()
}
