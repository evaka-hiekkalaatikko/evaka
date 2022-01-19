// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.placement

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import fi.espoo.evaka.FullApplicationTest
import fi.espoo.evaka.application.ApplicationStatus
import fi.espoo.evaka.daycare.setUnitFeatures
import fi.espoo.evaka.insertApplication
import fi.espoo.evaka.insertGeneralTestFixtures
import fi.espoo.evaka.pis.service.insertGuardian
import fi.espoo.evaka.serviceneed.insertServiceNeed
import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.asUser
import fi.espoo.evaka.shared.dev.DevDaycareGroup
import fi.espoo.evaka.shared.dev.insertTestDaycareGroup
import fi.espoo.evaka.shared.dev.insertTestPlacement
import fi.espoo.evaka.shared.dev.resetDatabase
import fi.espoo.evaka.shared.security.PilotFeature
import fi.espoo.evaka.snPreschoolDaycare45
import fi.espoo.evaka.snPreschoolDaycarePartDay35
import fi.espoo.evaka.test.getApplicationStatus
import fi.espoo.evaka.testAdult_1
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testChild_2
import fi.espoo.evaka.testDaycare
import fi.espoo.evaka.testDaycare2
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacementControllerCitizenIntegrationTest : FullApplicationTest() {
    private final val child = testChild_1
    private final val child2 = testChild_2
    private final val parent = testAdult_1
    private final val authenticatedParent = AuthenticatedUser.Citizen(parent.id.raw)

    private final val daycareId = testDaycare.id
    private final val daycare2Id = testDaycare2.id

    private final val today = LocalDate.now()

    private final val placementStart = today.minusMonths(3)
    private final val placementEnd = placementStart.plusMonths(6)

    @BeforeEach
    fun setUp() {
        db.transaction { tx ->
            tx.resetDatabase()
            tx.insertGeneralTestFixtures()
            tx.insertTestPlacement(
                childId = child.id,
                unitId = daycareId,
                startDate = placementStart,
                endDate = placementEnd
            )
            tx.insertTestPlacement(
                childId = child.id,
                unitId = daycare2Id,
                startDate = placementEnd.plusDays(1),
                endDate = placementEnd.plusMonths(2)
            )
            tx.insertTestDaycareGroup(DevDaycareGroup(daycareId = daycareId))
            tx.insertTestDaycareGroup(DevDaycareGroup(daycareId = daycare2Id))

            tx.insertGuardian(parent.id, child.id)
            tx.insertGuardian(parent.id, child2.id)
        }
    }

    @Test
    fun `child placements are returned`() {
        val childPlacements = getChildPlacements(child.id)
        assertEquals(2, childPlacements.size)
        assertEquals(placementStart, childPlacements[0].startDate)
        assertEquals(placementEnd, childPlacements[0].endDate)
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.DAYCARE, childPlacements[0].type)
        assertEquals(1, childPlacements[0].placements.size)
        assertEquals(PlacementType.DAYCARE, childPlacements[0].placements[0].type)
        assertEquals(null, childPlacements[0].placements[0].terminationRequestedDate)
        assertEquals(null, childPlacements[0].placements[0].terminatedBy)
        assertTrue(childPlacements[0].terminatable)

        assertFalse(childPlacements[1].terminatable)
        assertEquals(listOf(false), childPlacements[1].placements.map { it.terminatable })
    }

    @Test
    fun `citizen can terminate own child's placement starting from tomorrow`() {
        val placementTerminationDate = today.plusDays(1)

        terminatePlacements(
            child.id,
            PlacementControllerCitizen.PlacementTerminationRequestBody(
                type = PlacementControllerCitizen.TerminatablePlacementType.DAYCARE,
                terminationDate = placementTerminationDate,
                unitId = daycareId,
                terminateDaycareOnly = false,
            )
        )

        val childPlacements = getChildPlacements(child.id)
        assertEquals(2, childPlacements.size)
        assertEquals(placementStart, childPlacements[0].startDate)
        assertEquals(placementTerminationDate, childPlacements[0].endDate)
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.DAYCARE, childPlacements[0].type)
        assertEquals(1, childPlacements[0].placements.size)
        assertEquals(today, childPlacements[0].placements[0].terminationRequestedDate)
        assertEquals("${parent.firstName} ${parent.lastName}", childPlacements[0].placements[0].terminatedBy?.name)

        assertEquals(false, childPlacements[1].terminatable)
        assertEquals(listOf(null), childPlacements[1].placements.map { it.terminationRequestedDate })
    }

    @Test
    fun `terminating preschool also terminates upcoming daycare placements`() {
        val placementTerminationDate = today.plusWeeks(1)

        val startPreschool = LocalDate.now().minusWeeks(2)
        val endPreschool = startPreschool.plusMonths(1)
        val startDaycare = endPreschool.plusDays(1)
        val endDaycare = startDaycare.plusMonths(1)
        val startClub = endDaycare.plusDays(1)
        val endClub = startClub.plusMonths(1)
        val childId = child2.id
        db.transaction {
            it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                // placement in the past unaffected
                startDate = LocalDate.now().minusYears(3),
                endDate = LocalDate.now().minusYears(2),
                type = PlacementType.DAYCARE,
            )
            it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                // placement in the past unaffected
                startDate = LocalDate.now().minusMonths(12),
                endDate = LocalDate.now().minusMonths(6),
                type = PlacementType.PRESCHOOL,
            )
            it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                startDate = startPreschool,
                endDate = endPreschool,
                type = PlacementType.PRESCHOOL,
            )
            it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                startDate = startDaycare,
                endDate = endDaycare,
                type = PlacementType.DAYCARE,
            )
            it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                startDate = startClub,
                endDate = endClub,
                type = PlacementType.CLUB,
            )
        }

        terminatePlacements(
            childId,
            PlacementControllerCitizen.PlacementTerminationRequestBody(
                type = PlacementControllerCitizen.TerminatablePlacementType.PRESCHOOL,
                terminationDate = placementTerminationDate,
                unitId = daycareId,
                terminateDaycareOnly = false,
            )
        )

        val childPlacements = getChildPlacements(childId)

        assertEquals(2, childPlacements.size)
        val preschoolPlacement = childPlacements[0]
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.PRESCHOOL, preschoolPlacement.type)
        assertEquals(startPreschool, preschoolPlacement.startDate)
        assertEquals(placementTerminationDate, preschoolPlacement.endDate)
        assertEquals(1, preschoolPlacement.placements.size)
        assertEquals(today, preschoolPlacement.placements[0].terminationRequestedDate)
        assertEquals("${parent.firstName} ${parent.lastName}", preschoolPlacement.placements[0].terminatedBy?.name)
        assertEquals(PlacementType.PRESCHOOL, preschoolPlacement.placements[0].type)

        // club placement is unaffected
        val clubPlacement = childPlacements[1]
        assertEquals(startClub, clubPlacement.startDate)
        assertEquals(endClub, clubPlacement.endDate)
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.CLUB, clubPlacement.type)
        assertEquals(1, clubPlacement.placements.size)
        assertNull(clubPlacement.placements[0].terminationRequestedDate)
        assertEquals(PlacementType.CLUB, clubPlacement.placements[0].type)
    }

    @Test
    fun `terminating PRESCHOOL_DAYCARE with daycare only changes the remainder of the preschool to PRESCHOOL and terminates upcoming daycare`() {
        val placementTerminationDate = today.plusWeeks(1)

        val startPreschool = LocalDate.now().minusWeeks(2)
        val endPreschool = startPreschool.plusMonths(1)
        val startDaycare = endPreschool.plusDays(1)
        val endDaycare = startDaycare.plusMonths(1)
        val childId = child2.id
        db.transaction {
            val id = it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                startDate = startPreschool,
                endDate = endPreschool,
                type = PlacementType.PRESCHOOL_DAYCARE,
            )
            it.insertServiceNeed(
                id,
                startPreschool,
                endPreschool.minusDays(10),
                snPreschoolDaycare45.id,
                false,
                null,
                null
            )
            it.insertServiceNeed(
                id,
                endPreschool.minusDays(9),
                endPreschool,
                snPreschoolDaycarePartDay35.id,
                false,
                null,
                null
            )
            it.insertTestPlacement(
                childId = childId,
                unitId = daycareId,
                startDate = startDaycare,
                endDate = endDaycare,
                type = PlacementType.DAYCARE,
            )
        }

        val placementsBefore = getChildPlacements(childId)
        assertEquals(1, placementsBefore.size)
        val placementBefore = placementsBefore[0]
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.PRESCHOOL, placementBefore.type)
        assertEquals(startPreschool, placementBefore.startDate)
        assertEquals(endPreschool, placementBefore.endDate)

        assertEquals(listOf(PlacementType.PRESCHOOL_DAYCARE), placementBefore.placements.map { it.type })
        assertEquals(listOf(PlacementType.DAYCARE), placementBefore.additionalPlacements.map { it.type })

        terminatePlacements(
            childId,
            PlacementControllerCitizen.PlacementTerminationRequestBody(
                type = PlacementControllerCitizen.TerminatablePlacementType.PRESCHOOL,
                terminationDate = placementTerminationDate,
                unitId = daycareId,
                terminateDaycareOnly = true,
            )
        )

        val childPlacements = getChildPlacements(childId)

        assertEquals(1, childPlacements.size)
        val first = childPlacements[0]
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.PRESCHOOL, first.type)
        assertEquals(startPreschool, first.startDate)
        assertEquals(endPreschool, first.endDate)
        assertEquals(2, first.placements.size)
        assertEquals(0, first.additionalPlacements.size)

        val currentPlacement = first.placements[0]
        assertEquals(startPreschool, currentPlacement.startDate)
        assertEquals(placementTerminationDate, currentPlacement.endDate)
        assertEquals(PlacementType.PRESCHOOL_DAYCARE, currentPlacement.type)
        assertNull(currentPlacement.terminationRequestedDate)
        assertNull(currentPlacement.terminatedBy)

        val remainderOfPreschool = first.placements[1]
        assertNull(remainderOfPreschool.terminationRequestedDate)
        assertEquals(PlacementType.PRESCHOOL, remainderOfPreschool.type)
        assertEquals(placementTerminationDate.plusDays(1), remainderOfPreschool.startDate)
        assertEquals(endPreschool, remainderOfPreschool.endDate)
    }

    @Test
    fun `placement cannot be terminated if placement termination is not in unit's enabled features`() {
        val body = PlacementControllerCitizen.PlacementTerminationRequestBody(
            type = PlacementControllerCitizen.TerminatablePlacementType.DAYCARE,
            terminationDate = today.plusDays(1),
            unitId = daycare2Id,
            terminateDaycareOnly = false,
        )
        terminatePlacements(child.id, body, 403)
        db.transaction { it.setUnitFeatures(daycare2Id, setOf(PilotFeature.PLACEMENT_TERMINATION)) }
        terminatePlacements(child.id, body, 200)
    }

    @Test
    fun `transfer application with preferred date after requested termination date is cancelled`() {
        val placementTerminationDate = today.plusDays(1)

        val applicationBeforeTermination = db.transaction { tx ->
            // given
            tx.insertApplication(
                guardian = parent,
                child = child,
                applicationId = ApplicationId(UUID.randomUUID()),
                preferredStartDate = placementTerminationDate.plusDays(-1),
                transferApplication = true
            )
        }

        val applicationAfterTermination = db.transaction { tx ->
            tx.insertApplication(
                guardian = parent,
                child = child,
                applicationId = ApplicationId(UUID.randomUUID()),
                preferredStartDate = placementTerminationDate.plusDays(1),
                transferApplication = true
            )
        }

        terminatePlacements(
            child.id,
            PlacementControllerCitizen.PlacementTerminationRequestBody(
                type = PlacementControllerCitizen.TerminatablePlacementType.DAYCARE,
                terminationDate = placementTerminationDate,
                unitId = daycareId,
                terminateDaycareOnly = false,
            )
        )

        assertEquals(ApplicationStatus.CREATED, db.read { it.getApplicationStatus(applicationBeforeTermination.id) })
        assertEquals(ApplicationStatus.CANCELLED, db.read { it.getApplicationStatus(applicationAfterTermination.id) })

        val childPlacements = getChildPlacements(child.id)
        assertEquals(2, childPlacements.size)
        assertEquals(placementStart, childPlacements[0].startDate)
        assertEquals(placementTerminationDate, childPlacements[0].endDate)
        assertEquals(PlacementControllerCitizen.TerminatablePlacementType.DAYCARE, childPlacements[0].type)
        assertEquals(1, childPlacements[0].placements.size)
        assertEquals(today, childPlacements[0].placements[0].terminationRequestedDate)
        assertEquals("${parent.firstName} ${parent.lastName}", childPlacements[0].placements[0].terminatedBy?.name)

        assertEquals(listOf(null), (childPlacements[1].placements + childPlacements[1].additionalPlacements).map { it.terminationRequestedDate })
    }

    private fun terminatePlacements(
        childId: ChildId,
        termination: PlacementControllerCitizen.PlacementTerminationRequestBody,
        expectedStatus: Int = 200
    ) {
        http.post("/citizen/children/$childId/placements/terminate")
            .jsonBody(objectMapper.writeValueAsString(termination))
            .asUser(authenticatedParent)
            .response()
            .also { assertEquals(expectedStatus, it.second.statusCode) }
    }

    private fun getChildPlacements(childId: ChildId): List<PlacementControllerCitizen.TerminatablePlacementGroup> {
        return http.get("/citizen/children/$childId/placements")
            .asUser(authenticatedParent)
            .responseObject<PlacementControllerCitizen.ChildPlacementResponse>(objectMapper)
            .also { assertEquals(200, it.second.statusCode) }
            .third.get().placements
    }
}
