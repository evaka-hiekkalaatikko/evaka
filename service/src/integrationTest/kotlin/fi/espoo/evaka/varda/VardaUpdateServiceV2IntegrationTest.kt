// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.varda

import fi.espoo.evaka.FixtureBuilder
import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.insertGeneralTestFixtures
import fi.espoo.evaka.invoicing.createFeeDecisionChildFixture
import fi.espoo.evaka.invoicing.createFeeDecisionFixture
import fi.espoo.evaka.invoicing.createVoucherValueDecisionFixture
import fi.espoo.evaka.invoicing.data.upsertFeeDecisions
import fi.espoo.evaka.invoicing.data.upsertValueDecisions
import fi.espoo.evaka.invoicing.domain.FeeDecisionServiceNeed
import fi.espoo.evaka.invoicing.domain.FeeDecisionStatus
import fi.espoo.evaka.invoicing.domain.FeeDecisionType
import fi.espoo.evaka.invoicing.domain.PersonData
import fi.espoo.evaka.invoicing.domain.VoucherValueDecision
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionServiceNeed
import fi.espoo.evaka.invoicing.domain.VoucherValueDecisionStatus
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.resetDatabase
import fi.espoo.evaka.serviceneednew.ServiceNeedOption
import fi.espoo.evaka.serviceneednew.deleteNewServiceNeed
import fi.espoo.evaka.shared.db.Database
import fi.espoo.evaka.shared.dev.DevPerson
import fi.espoo.evaka.shared.dev.insertTestPerson
import fi.espoo.evaka.shared.dev.insertVardaServiceNeed
import fi.espoo.evaka.shared.domain.DateRange
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.snDefaultDaycare
import fi.espoo.evaka.snDefaultPartDayDaycare
import fi.espoo.evaka.testAdult_1
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testDaycare
import fi.espoo.evaka.testDecisionMaker_1
import fi.espoo.evaka.varda.integration.MockVardaIntegrationEndpoint
import org.jdbi.v3.core.kotlin.mapTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class VardaUpdateServiceV2IntegrationTest : FullApplicationTest() {
    @Autowired
    lateinit var mockEndpoint: MockVardaIntegrationEndpoint

    @BeforeEach
    fun beforeEach() {
        db.transaction {
            it.insertGeneralTestFixtures()
        }
        insertVardaUnit(db)
        mockEndpoint.cleanUp()
    }

    @AfterEach
    fun afterEach() {
        db.transaction { it.resetDatabase() }
        mockEndpoint.cleanUp()
    }

    val VOUCHER_VALUE = 10000
    val VOUCHER_CO_PAYMENT = 5000

    @Test
    fun `calculateServiceNeedsChanges finds changed service need since given moment`() {
        val earliestIncludedTime = HelsinkiDateTime.now()
        val startDate = earliestIncludedTime.minusDays(20).toLocalDate()
        val endDate = startDate.plusDays(10)
        val shouldBeIncluded = earliestIncludedTime.plusHours(1)
        val shouldBeExcluded = earliestIncludedTime.plusHours(-1)
        val includedServiceNeedId = createServiceNeed(
            db, shouldBeIncluded,
            snDefaultDaycare.copy(
                updated = shouldBeIncluded
            ),
            testChild_1, startDate, endDate
        )
        createServiceNeed(
            db, shouldBeExcluded,
            snDefaultPartDayDaycare.copy(
                updated = shouldBeExcluded
            ),
            testChild_1, endDate.plusDays(1)
        )

        db.read {
            val changes = it.getEvakaServiceNeedChanges(earliestIncludedTime)
            assertEquals(1, changes.size)
            assertEquals(includedServiceNeedId, changes.get(0).evakaServiceNeedId)
            assertEquals(shouldBeIncluded, changes.get(0).evakaLastUpdated)
        }
    }

    @Test
    fun `calculateServiceNeedsChanges finds changed service need option since given moment`() {
        val earliestIncludedTime = HelsinkiDateTime.now()
        val startDate = earliestIncludedTime.minusDays(20).toLocalDate()
        val endDate = startDate.plusDays(10)
        val shouldBeIncluded = earliestIncludedTime.plusHours(1)
        val shouldBeExcluded = earliestIncludedTime.plusHours(-1)
        val includedServiceNeedId = createServiceNeed(
            db, shouldBeExcluded,
            snDefaultDaycare.copy(
                updated = shouldBeIncluded
            ),
            testChild_1, startDate, endDate
        )
        createServiceNeed(
            db, shouldBeExcluded,
            snDefaultPartDayDaycare.copy(
                updated = shouldBeExcluded
            ),
            testChild_1, endDate.plusDays(1)
        )

        db.read {
            val changes = it.getEvakaServiceNeedChanges(earliestIncludedTime)
            assertEquals(1, changes.size)
            assertEquals(includedServiceNeedId, changes.get(0).evakaServiceNeedId)
            assertEquals(shouldBeIncluded, changes.get(0).evakaLastUpdated)
        }
    }

    @Test
    fun `calculateEvakaVsVardaServiceNeedChangesByChild finds new evaka service need when varda has none`() {
        val since = HelsinkiDateTime.now()
        val option = snDefaultDaycare.copy(updated = since)
        val snId = createServiceNeed(db, since, option)
        val childId = db.read { it.getChidIdByServiceNeedId(snId) }

        val diffs = calculateEvakaVsVardaServiceNeedChangesByChild(db, since)
        assertEquals(1, diffs.keys.size)
        assertServiceNeedDiffSizes(diffs.get(childId), 1, 0, 0)
        assertEquals(snId, diffs.get(childId)?.additions?.get(0))
    }

    @Test
    fun `calculateEvakaVsVardaServiceNeedChangesByChild finds updated evaka service need`() {
        val since = HelsinkiDateTime.now()
        val option = snDefaultDaycare.copy(updated = since)
        val snId = createServiceNeed(db, since, option)
        val childId = db.read { it.getChidIdByServiceNeedId(snId) } ?: throw Exception("Created service need not found?!?")
        db.transaction {
            it.insertVardaServiceNeed(
                VardaServiceNeed(
                    evakaChildId = childId,
                    evakaServiceNeedId = snId,
                    evakaServiceNeedOptionId = snDefaultDaycare.id,
                    evakaServiceNeedUpdated = since.minusHours(100), // Evaka and varda timestamps differ, thus sn has changed
                    evakaServiceNeedOptionUpdated = since.minusHours(100)
                )
            )
        }

        val diffs = calculateEvakaVsVardaServiceNeedChangesByChild(db, since)
        assertEquals(1, diffs.keys.size)
        assertServiceNeedDiffSizes(diffs.get(childId), 0, 1, 0)
        assertEquals(snId, diffs.get(childId)?.updates?.get(0))
    }

    @Test
    fun `calculateEvakaVsVardaServiceNeedChangesByChild finds deleted evaka service need when there are also other changes`() {
        val since = HelsinkiDateTime.now()
        val option = snDefaultDaycare.copy(updated = since)
        val changedServiceNeedId = createServiceNeed(db, since, option)
        val childId = db.read { it.getChidIdByServiceNeedId(changedServiceNeedId) } ?: throw Exception("Created service need not found?!?")
        db.transaction {
            it.insertVardaServiceNeed(
                VardaServiceNeed(
                    evakaChildId = childId,
                    evakaServiceNeedId = changedServiceNeedId,
                    evakaServiceNeedOptionId = snDefaultDaycare.id,
                    evakaServiceNeedUpdated = since.minusHours(100), // Evaka and varda timestamps differ, thus sn has changed
                    evakaServiceNeedOptionUpdated = since.minusHours(100)
                )
            )
        }

        val deletedSnId = UUID.randomUUID()

        db.transaction {
            it.insertVardaServiceNeed(
                VardaServiceNeed(
                    evakaChildId = childId,
                    evakaServiceNeedId = deletedSnId, // Does not exist in evaka, thus should be deleted
                    evakaServiceNeedOptionId = snDefaultDaycare.id,
                    evakaServiceNeedUpdated = since.minusHours(100),
                    evakaServiceNeedOptionUpdated = since.minusHours(100)
                )
            )
        }

        val diffs = calculateEvakaVsVardaServiceNeedChangesByChild(db, since)
        assertEquals(1, diffs.keys.size)
        assertServiceNeedDiffSizes(diffs.get(childId), 0, 1, 1)
        assertEquals(changedServiceNeedId, diffs.get(childId)?.updates?.get(0))
        assertEquals(deletedSnId, diffs.get(childId)?.deletes?.get(0))
    }

    @Test
    fun `calculateEvakaVsVardaServiceNeedChangesByChild finds removed evaka service needs that exists in varda and there are no other changes`() {
        val since = HelsinkiDateTime.now()
        val childId: UUID = UUID.randomUUID()
        val deletedSnId = UUID.randomUUID()
        db.transaction {
            it.insertTestPerson(
                DevPerson(
                    id = childId,
                    dateOfBirth = since.plusYears(-5).toLocalDate(),
                    ssn = "260718A384E"
                )
            )

            it.insertVardaServiceNeed(
                VardaServiceNeed(
                    evakaChildId = childId,
                    evakaServiceNeedId = deletedSnId, // Does not exist in evaka, thus should be deleted
                    evakaServiceNeedOptionId = snDefaultDaycare.id,
                    evakaServiceNeedUpdated = since.minusHours(100),
                    evakaServiceNeedOptionUpdated = since.minusHours(100)
                )
            )
        }

        val diffs = calculateEvakaVsVardaServiceNeedChangesByChild(db, since)
        assertEquals(1, diffs.keys.size)
        assertServiceNeedDiffSizes(diffs.get(childId), 0, 0, 1)
        assertEquals(deletedSnId, diffs.get(childId)?.deletes?.get(0))
    }

    @Test
    fun `calculateEvakaFeeDataChangesByServiceNeed sees only a changed fee decision`() {
        val since = HelsinkiDateTime.now()
        val startDate = since.minusDays(100).toLocalDate()
        val snId = createServiceNeed(db, since, snDefaultDaycare, testChild_1, startDate)

        val includedFdId = createFeeDecision(db, testChild_1, DateRange(startDate, startDate.plusDays(10)), since.toInstant())
        val includedFeeDecisionChildIds = db.read { it.getFeeDecisionChildIdsByFeeDecisionId(includedFdId) }
        createFeeDecision(db, testChild_1, DateRange(startDate.plusDays(11), startDate.plusDays(20)), since.minusDays(1).toInstant())

        val snFeeDiff = db.read { it.calculateEvakaFeeDataChangesByServiceNeed(since) }
        assertEquals(1, snFeeDiff.size)
        assertEquals(snId, snFeeDiff.get(0).serviceNeedId)
        assertEquals(testChild_1.id, snFeeDiff.get(0).evakaChildId)
        assertEquals(1, snFeeDiff.get(0).feeDecisionIds.size)
        assertEquals(includedFeeDecisionChildIds.get(0), snFeeDiff.get(0).feeDecisionIds.get(0))
        assertEquals(0, snFeeDiff.get(0).voucherValueDecisionIds.size)
    }

    @Test
    fun `calculateEvakaFeeDataChangesByServiceNeed matches fee decisions correctly to service needs`() {
        val since = HelsinkiDateTime.now()
        val startDate = since.minusDays(100).toLocalDate()
        val endDate = since.minusDays(90).toLocalDate()
        val startDate2 = endDate.plusDays(1)
        val snId1 = createServiceNeed(db, since, snDefaultDaycare, testChild_1, startDate, endDate)
        val snId2 = createServiceNeed(db, since, snDefaultDaycare, testChild_1, startDate2)

        val fdId1 = createFeeDecision(db, testChild_1, DateRange(startDate, endDate), since.toInstant())
        val includedFeeDecisionChildIds1 = db.read { it.getFeeDecisionChildIdsByFeeDecisionId(fdId1) }

        val fdId2 = createFeeDecision(db, testChild_1, DateRange(startDate2, null), since.toInstant())
        val includedFeeDecisionChildIds2 = db.read { it.getFeeDecisionChildIdsByFeeDecisionId(fdId2) }

        val snFeeDiff = db.read { it.calculateEvakaFeeDataChangesByServiceNeed(since) }
        assertEquals(2, snFeeDiff.size)
        assertEquals(includedFeeDecisionChildIds1.get(0), snFeeDiff.find { it.serviceNeedId == snId1 }?.feeDecisionIds?.get(0))
        assertEquals(includedFeeDecisionChildIds2.get(0), snFeeDiff.find { it.serviceNeedId == snId2 }?.feeDecisionIds?.get(0))
    }

    @Test
    fun `calculateEvakaFeeDataChangesByServiceNeed matches voucher value decisions correctly to service needs`() {
        val since = HelsinkiDateTime.now()
        val startDate = since.minusDays(100).toLocalDate()
        val endDate = since.minusDays(90).toLocalDate()
        val startDate2 = endDate.plusDays(1)
        val snId1 = createServiceNeed(db, since, snDefaultDaycare, testChild_1, startDate, endDate)
        val snId2 = createServiceNeed(db, since, snDefaultDaycare, testChild_1, startDate2)
        val vd1 = createVoucherDecision(db, startDate, startDate, testDaycare.id, 100, 100, testAdult_1.id, testChild_1, since.toInstant())
        val vd2 = createVoucherDecision(db, startDate2, null, testDaycare.id, 100, 100, testAdult_1.id, testChild_1, since.toInstant())

        val snFeeDiff = db.read { it.calculateEvakaFeeDataChangesByServiceNeed(since) }
        assertEquals(2, snFeeDiff.size)
        assertEquals(vd1.id, snFeeDiff.find { it.serviceNeedId == snId1 }?.voucherValueDecisionIds?.get(0))
        assertEquals(vd2.id, snFeeDiff.find { it.serviceNeedId == snId2 }?.voucherValueDecisionIds?.get(0))
    }

    @Test
    fun `updateChildData created related varda decision data when evaka service need is added`() {
        insertVardaChild(db, testChild_1.id)
        val since = HelsinkiDateTime.now()
        val serviceNeedPeriod = DateRange(since.minusDays(100).toLocalDate(), since.toLocalDate())
        val feeDecisionPeriod = DateRange(serviceNeedPeriod.start, serviceNeedPeriod.start.plusDays(10))
        val voucherDecisionPeriod = DateRange(feeDecisionPeriod.end!!.plusDays(1), null)
        createServiceNeedAndFeeData(testChild_1, testAdult_1, since, serviceNeedPeriod, feeDecisionPeriod, voucherDecisionPeriod)

        updateChildData(db, vardaClient, since)

        assertVardaElementCounts(1, 1, 2)
        val vardaDecision = mockEndpoint.decisions.values.elementAt(0)
        assertVardaDecision(vardaDecision, serviceNeedPeriod.start, serviceNeedPeriod.end!!, serviceNeedPeriod.start, 1, snDefaultDaycare.daycareHoursPerWeek.toDouble())
        assertVardaFeeData(
            VardaFeeData(
                huoltajat = listOf(asVardaGuardian(testAdult_1)),
                maksun_peruste_koodi = "MP03",
                asiakasmaksu = 289.0,
                palveluseteli_arvo = 0.0,
                alkamis_pvm = feeDecisionPeriod.start,
                paattymis_pvm = feeDecisionPeriod.end,
                perheen_koko = 2,
                lahdejarjestelma = "SourceSystemVarda",
                lapsi = "not asserted"
            ),
            mockEndpoint.feeData.values.elementAt(0)
        )
        assertVardaFeeData(
            VardaFeeData(
                huoltajat = listOf(asVardaGuardian(testAdult_1)),
                maksun_peruste_koodi = "MP03",
                asiakasmaksu = 50.0,
                palveluseteli_arvo = 100.0,
                alkamis_pvm = voucherDecisionPeriod.start,
                paattymis_pvm = serviceNeedPeriod.end,
                perheen_koko = 2,
                lahdejarjestelma = "SourceSystemVarda",
                lapsi = "not asserted"
            ),
            mockEndpoint.feeData.values.elementAt(1)
        )
    }

    @Test
    fun `updateChildData removes all related varda data when service need is removed from evaka`() {
        insertVardaChild(db, testChild_1.id)
        val since = HelsinkiDateTime.now()
        val serviceNeedPeriod = DateRange(since.minusDays(100).toLocalDate(), since.toLocalDate())
        val feeDecisionPeriod = DateRange(serviceNeedPeriod.start, serviceNeedPeriod.start.plusDays(10))
        val voucherDecisionPeriod = DateRange(feeDecisionPeriod.end!!.plusDays(1), null)
        val id = createServiceNeedAndFeeData(testChild_1, testAdult_1, since, serviceNeedPeriod, feeDecisionPeriod, voucherDecisionPeriod)

        updateChildData(db, vardaClient, since)

        assertVardaElementCounts(1, 1, 2)

        db.transaction { it.deleteNewServiceNeed(id) }
        updateChildData(db, vardaClient, since)

        assertVardaElementCounts(0, 0, 0)
    }

    @Test
    fun `updateChildData sends child service need to varda only when all required data exists in evaka`() {
        insertVardaChild(db, testChild_1.id)
        val since = HelsinkiDateTime.now()
        val serviceNeedPeriod = DateRange(since.minusDays(100).toLocalDate(), since.toLocalDate())
        val id = createServiceNeed(db, since, snDefaultDaycare, testChild_1, serviceNeedPeriod.start, serviceNeedPeriod.end!!)

        updateChildData(db, vardaClient, since)

        assertVardaElementCounts(0, 0, 0)

        val feeDecisionPeriod = DateRange(serviceNeedPeriod.start, serviceNeedPeriod.start.plusDays(10))
        val voucherDecisionPeriod = DateRange(feeDecisionPeriod.end!!.plusDays(1), null)
        createFeeDecision(db, testChild_1, DateRange(feeDecisionPeriod.start, feeDecisionPeriod.end), since.toInstant())
        createVoucherDecision(db, voucherDecisionPeriod.start, voucherDecisionPeriod.end, testDaycare.id, VOUCHER_VALUE, VOUCHER_CO_PAYMENT, testAdult_1.id, testChild_1, since.toInstant())

        updateChildData(db, vardaClient, since)

        assertVardaElementCounts(1, 1, 2)

        val vardaDecision = mockEndpoint.decisions.values.elementAt(0)
        assertVardaDecision(vardaDecision, serviceNeedPeriod.start, serviceNeedPeriod.end!!, serviceNeedPeriod.start, 1, snDefaultDaycare.daycareHoursPerWeek.toDouble())
        assertVardaFeeData(
            VardaFeeData(
                huoltajat = listOf(asVardaGuardian(testAdult_1)),
                maksun_peruste_koodi = "MP03",
                asiakasmaksu = 289.0,
                palveluseteli_arvo = 0.0,
                alkamis_pvm = feeDecisionPeriod.start,
                paattymis_pvm = feeDecisionPeriod.end,
                perheen_koko = 2,
                lahdejarjestelma = "SourceSystemVarda",
                lapsi = "not asserted"
            ),
            mockEndpoint.feeData.values.elementAt(0)
        )
        assertVardaFeeData(
            VardaFeeData(
                huoltajat = listOf(asVardaGuardian(testAdult_1)),
                maksun_peruste_koodi = "MP03",
                asiakasmaksu = 50.0,
                palveluseteli_arvo = 100.0,
                alkamis_pvm = voucherDecisionPeriod.start,
                paattymis_pvm = serviceNeedPeriod.end,
                perheen_koko = 2,
                lahdejarjestelma = "SourceSystemVarda",
                lapsi = "not asserted"
            ),
            mockEndpoint.feeData.values.elementAt(1)
        )
    }

    private fun createServiceNeedAndFeeData(
        child: PersonData.Detailed,
        adult: PersonData.Detailed,
        since: HelsinkiDateTime,
        serviceNeedPeriod: DateRange,
        feeDecisionPeriod: DateRange,
        voucherDecisionPeriod: DateRange
    ): UUID {
        val id = createServiceNeed(db, since, snDefaultDaycare, child, serviceNeedPeriod.start, serviceNeedPeriod.end!!)
        createFeeDecision(db, child, DateRange(feeDecisionPeriod.start, feeDecisionPeriod.end), since.toInstant())
        createVoucherDecision(db, voucherDecisionPeriod.start, voucherDecisionPeriod.end, testDaycare.id, VOUCHER_VALUE, VOUCHER_CO_PAYMENT, adult.id, child, since.toInstant())
        return id
    }

    private fun asVardaGuardian(g: PersonData.Detailed): VardaGuardian = VardaGuardian(henkilotunnus = g.ssn ?: "", etunimet = g.firstName, sukunimi = g.lastName)

    /*
    TODO assert these
    data class VardaDecision(
    @JsonProperty("pikakasittely_kytkin")
    val urgent: Boolean,
    @JsonProperty("tilapainen_vaka_kytkin")
    val temporary: Boolean,
    @JsonProperty("paivittainen_vaka_kytkin")
    val daily: Boolean,
    @JsonProperty("vuorohoito_kytkin")
    val shiftCare: Boolean,
    @JsonProperty("jarjestamismuoto_koodi")
    val providerTypeCode: String,
) {
    @JsonProperty("kokopaivainen_vaka_kytkin")
    val fullDay: Boolean = hoursPerWeek >= 25
}
     */
    private fun assertVardaDecision(
        vardaDecision: VardaDecision,
        expectedStartDate: LocalDate,
        expectedEndDate: LocalDate,
        expectedApplicationDate: LocalDate,
        expectedChildId: Long,
        expectedHoursPerWeek: Double
    ) {
        assertEquals(true, vardaDecision.childUrl.endsWith("$expectedChildId/"), "Expected ${vardaDecision.childUrl} to end with $expectedChildId")
        assertEquals(expectedStartDate, vardaDecision.startDate)
        assertEquals(expectedEndDate, vardaDecision.endDate)
        assertEquals(expectedApplicationDate, vardaDecision.applicationDate)
        assertEquals(expectedHoursPerWeek, vardaDecision.hoursPerWeek)
    }

    private fun assertVardaFeeData(expectedVardaFeeData: VardaFeeData, vardaFeeData: VardaFeeData) {
        expectedVardaFeeData.huoltajat.forEach { expectedHuoltaja -> assertEquals(true, vardaFeeData.huoltajat.any { g -> g.henkilotunnus == expectedHuoltaja.henkilotunnus }) }
        assertEquals(expectedVardaFeeData.maksun_peruste_koodi, vardaFeeData.maksun_peruste_koodi)
        assertEquals(expectedVardaFeeData.asiakasmaksu, vardaFeeData.asiakasmaksu)
        assertEquals(expectedVardaFeeData.palveluseteli_arvo, vardaFeeData.palveluseteli_arvo)
        assertEquals(expectedVardaFeeData.alkamis_pvm, vardaFeeData.alkamis_pvm)
        assertEquals(expectedVardaFeeData.paattymis_pvm, vardaFeeData.paattymis_pvm)
        assertEquals(expectedVardaFeeData.perheen_koko, vardaFeeData.perheen_koko)
        assertEquals(expectedVardaFeeData.lahdejarjestelma, vardaFeeData.lahdejarjestelma)
    }

    private fun assertVardaElementCounts(expectedDecisionCount: Int, expectedPlacementCount: Int, expectedFeeDataCount: Int) {
        assertEquals(expectedDecisionCount, mockEndpoint.decisions.values.size, "Expected varda decision count $expectedDecisionCount does not match ${mockEndpoint.decisions.values.size}")
        assertEquals(expectedPlacementCount, mockEndpoint.placements.values.size, "Expected varda placement count $expectedPlacementCount does not match ${mockEndpoint.placements.values.size}")
        assertEquals(expectedFeeDataCount, mockEndpoint.feeData.values.size, "Expected varda fee data count $expectedFeeDataCount does not match ${mockEndpoint.feeData.values.size}")
    }

    private fun assertServiceNeedDiffSizes(diff: VardaChildCalculatedServiceNeedChanges?, expectedAdditions: Int, expectedUpdates: Int, expectedDeletes: Int) {
        assertNotNull(diff)
        if (diff != null) {
            assertEquals(expectedAdditions, diff.additions.size)
            assertEquals(expectedUpdates, diff.updates.size)
            assertEquals(expectedDeletes, diff.deletes.size)
        }
    }

    private fun createServiceNeed(db: Database.Connection, updated: HelsinkiDateTime, option: ServiceNeedOption, child: PersonData.Detailed = testChild_1, fromDays: LocalDate = HelsinkiDateTime.now().minusDays(100).toLocalDate(), toDays: LocalDate = HelsinkiDateTime.now().toLocalDate()): UUID {
        var serviceNeedId = UUID.randomUUID()
        db.transaction { tx ->
            FixtureBuilder(tx, HelsinkiDateTime.now().toLocalDate())
                .addChild().usePerson(child).saveAnd {
                    addPlacement().ofType(PlacementType.DAYCARE).toUnit(testDaycare.id).fromDay(fromDays).toDay(toDays).saveAnd {
                        addServiceNeed()
                            .withId(serviceNeedId)
                            .withUpdated(updated)
                            .createdBy(testDecisionMaker_1.id)
                            .withOption(option)
                            .save()
                    }
                }
        }
        return serviceNeedId
    }

    private fun createFeeDecision(db: Database.Connection, child: PersonData.Detailed, period: DateRange, sentAt: Instant): UUID {
        val fd = createFeeDecisionFixture(
            FeeDecisionStatus.SENT,
            FeeDecisionType.NORMAL,
            period,
            testAdult_1.id,
            listOf(
                createFeeDecisionChildFixture(
                    child.id,
                    child.dateOfBirth,
                    testDaycare.id,
                    PlacementType.DAYCARE,
                    snDefaultDaycare.toFeeDecisionServiceNeed()
                )
            ),
            sentAt = sentAt
        )
        db.transaction { tx -> tx.upsertFeeDecisions(listOf(fd)) }

        return fd.id
    }

    private fun createVoucherDecision(
        db: Database.Connection,
        validFrom: LocalDate,
        validTo: LocalDate?,
        unitId: UUID,
        value: Int,
        coPayment: Int,
        adultId: UUID,
        child: PersonData.Detailed,
        sentAt: Instant
    ): VoucherValueDecision {
        return db.transaction {
            val decision = createVoucherValueDecisionFixture(
                status = VoucherValueDecisionStatus.DRAFT,
                validFrom = validFrom,
                validTo = validTo,
                headOfFamilyId = adultId,
                childId = child.id,
                dateOfBirth = child.dateOfBirth,
                unitId = unitId,
                value = value,
                coPayment = coPayment,
                placementType = PlacementType.DAYCARE,
                serviceNeed = VoucherValueDecisionServiceNeed(
                    snDefaultDaycare.feeCoefficient,
                    snDefaultDaycare.voucherValueCoefficient,
                    snDefaultDaycare.feeDescriptionFi,
                    snDefaultDaycare.feeDescriptionSv,
                    snDefaultDaycare.voucherValueDescriptionFi,
                    snDefaultDaycare.voucherValueDescriptionSv
                ),
                sentAt = sentAt
            )
            it.upsertValueDecisions(listOf(decision))
            decision
        }
    }
}

private fun Database.Read.getChidIdByServiceNeedId(serviceNeedId: UUID): UUID? = createQuery(
    """
SELECT p.child_id FROM placement p LEFT JOIN new_service_need sn ON p.id = sn.placement_id
WHERE sn.id = :serviceNeedId
        """
).bind("serviceNeedId", serviceNeedId)
    .mapTo<UUID>()
    .first()

private fun ServiceNeedOption.toFeeDecisionServiceNeed() = FeeDecisionServiceNeed(
    feeCoefficient = this.feeCoefficient,
    descriptionFi = this.feeDescriptionFi,
    descriptionSv = this.feeDescriptionSv,
    missing = false
)

private fun Database.Read.getFeeDecisionChildIdsByFeeDecisionId(fdId: UUID) = createQuery(
    """
SELECT c.id
FROM new_fee_decision as d
LEFT JOIN new_fee_decision_child c ON d.id = c.fee_decision_id
WHERE d.id = :fdId
        """
).bind("fdId", fdId).mapTo<UUID>().list()
