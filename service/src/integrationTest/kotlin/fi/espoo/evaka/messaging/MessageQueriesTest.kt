// SPDX-FileCopyrightText: 2017-2021 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.messaging

import fi.espoo.evaka.PureJdbiTest
import fi.espoo.evaka.daycare.domain.Language
import fi.espoo.evaka.pis.service.insertGuardian
import fi.espoo.evaka.placement.PlacementType
import fi.espoo.evaka.shared.EmployeeId
import fi.espoo.evaka.shared.GroupId
import fi.espoo.evaka.shared.GroupPlacementId
import fi.espoo.evaka.shared.MessageAccountId
import fi.espoo.evaka.shared.MessageId
import fi.espoo.evaka.shared.MessageThreadId
import fi.espoo.evaka.shared.ParentshipId
import fi.espoo.evaka.shared.PersonId
import fi.espoo.evaka.shared.PlacementId
import fi.espoo.evaka.shared.auth.AuthenticatedUser
import fi.espoo.evaka.shared.auth.UserRole
import fi.espoo.evaka.shared.auth.insertDaycareAclRow
import fi.espoo.evaka.shared.dev.DevChild
import fi.espoo.evaka.shared.dev.DevDaycare
import fi.espoo.evaka.shared.dev.DevDaycareGroup
import fi.espoo.evaka.shared.dev.DevEmployee
import fi.espoo.evaka.shared.dev.DevPerson
import fi.espoo.evaka.shared.dev.insertTestCareArea
import fi.espoo.evaka.shared.dev.insertTestChild
import fi.espoo.evaka.shared.dev.insertTestDaycare
import fi.espoo.evaka.shared.dev.insertTestDaycareGroup
import fi.espoo.evaka.shared.dev.insertTestDaycareGroupPlacement
import fi.espoo.evaka.shared.dev.insertTestEmployee
import fi.espoo.evaka.shared.dev.insertTestParentship
import fi.espoo.evaka.shared.dev.insertTestPerson
import fi.espoo.evaka.shared.dev.insertTestPlacement
import fi.espoo.evaka.shared.domain.EvakaClock
import fi.espoo.evaka.shared.domain.HelsinkiDateTime
import fi.espoo.evaka.shared.domain.MockEvakaClock
import fi.espoo.evaka.shared.domain.RealEvakaClock
import fi.espoo.evaka.shared.security.AccessControl
import fi.espoo.evaka.shared.security.Action
import fi.espoo.evaka.shared.security.PilotFeature
import fi.espoo.evaka.shared.security.actionrule.DefaultActionRuleMapping
import fi.espoo.evaka.testArea
import fi.espoo.evaka.testChild_1
import fi.espoo.evaka.testDaycare
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MessageQueriesTest : PureJdbiTest(resetDbBeforeEach = true) {

    private val person1Id = PersonId(UUID.randomUUID())
    private val person2Id = PersonId(UUID.randomUUID())
    private val employee1Id = EmployeeId(UUID.randomUUID())
    private val employee2Id = EmployeeId(UUID.randomUUID())
    private val accessControl = AccessControl(DefaultActionRuleMapping())
    private lateinit var clock: EvakaClock
    private val sendTime = HelsinkiDateTime.of(LocalDate.of(2022, 5, 14), LocalTime.of(12, 11))
    private val readTime = sendTime.plusSeconds(30)

    @BeforeEach
    fun setUp() {
        clock = MockEvakaClock(HelsinkiDateTime.of(LocalDate.of(2022, 11, 8), LocalTime.of(13, 1)))
        db.transaction { tx ->
            tx.insertTestPerson(
                DevPerson(id = person1Id, firstName = "Firstname", lastName = "Person")
            )
            tx.insertTestPerson(
                DevPerson(id = person2Id, firstName = "Firstname", lastName = "Person Two")
            )
            listOf(person1Id, person2Id).forEach { tx.createPersonMessageAccount(it) }

            tx.insertTestEmployee(
                DevEmployee(id = employee1Id, firstName = "Firstname", lastName = "Employee")
            )
            tx.insertTestEmployee(
                DevEmployee(id = employee2Id, firstName = "Firstname", lastName = "Employee Two")
            )
            listOf(employee1Id, employee2Id).forEach { tx.upsertEmployeeMessageAccount(it) }
        }
    }

    @Test
    fun `a thread can be created`() {
        val (employeeAccount, person1Account, person2Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id),
                    it.getCitizenMessageAccount(person2Id)
                )
            }

        val content = "Content"
        val title = "Hello"
        createThread(title, content, employeeAccount, listOf(person1Account, person2Account))

        assertEquals(
            setOf(person1Account, person2Account),
            db.read {
                it.createQuery("SELECT recipient_id FROM message_recipients")
                    .mapTo<MessageAccountId>()
                    .toSet()
            }
        )
        assertEquals(
            content,
            db.read { it.createQuery("SELECT content FROM message_content").mapTo<String>().one() }
        )
        assertEquals(
            title,
            db.read { it.createQuery("SELECT title FROM message_thread").mapTo<String>().one() }
        )
        assertEquals(
            "Employee Firstname",
            db.read { it.createQuery("SELECT sender_name FROM message").mapTo<String>().one() }
        )
        assertEquals(
            setOf("Person Firstname", "Person Two Firstname"),
            db.read {
                    it.createQuery("SELECT recipient_names FROM message")
                        .mapTo<Array<String>>()
                        .one()
                }
                .toSet()
        )
    }

    @Test
    fun `messages received by account are grouped properly`() {
        val (employee1Account, employee2Account, person1Account, person2Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee2Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id),
                    it.getCitizenMessageAccount(person2Id)
                )
            }

        val thread1Id =
            createThread(
                "Hello",
                "Content",
                employee1Account,
                listOf(person1Account, person2Account),
                sendTime
            )
        val thread2Id =
            createThread(
                "Newest thread",
                "Content 2",
                employee1Account,
                listOf(person1Account),
                sendTime.plusSeconds(1)
            )
        createThread(
            "Lone Thread",
            "Alone",
            employee2Account,
            listOf(employee2Account),
            sendTime.plusSeconds(2)
        )

        // employee is not a recipient in any threads
        assertEquals(
            0,
            db.read { it.getReceivedThreads(readTime, employee1Account, 10, 1, "Espoo") }.data.size
        )
        val personResult = db.read { it.getThreads(readTime, person1Account, 10, 1, "Espoo") }
        assertEquals(2, personResult.data.size)

        val thread = personResult.data.first()
        assertEquals(thread2Id, thread.id)
        assertEquals("Newest thread", thread.title)

        // when the thread is marked read for person 1
        db.transaction { it.markThreadRead(RealEvakaClock(), person1Account, thread1Id) }

        // then the message has correct readAt
        val person1Threads = db.read { it.getThreads(readTime, person1Account, 10, 1, "Espoo") }
        assertEquals(2, person1Threads.data.size)
        val readMessages = person1Threads.data.flatMap { it.messages.mapNotNull { m -> m.readAt } }
        assertEquals(1, readMessages.size)
        assertTrue(HelsinkiDateTime.now().durationSince(readMessages[0]) < Duration.ofSeconds(5))

        // then person 2 threads are not affected
        assertEquals(
            0,
            db.read { it.getThreads(readTime, person2Account, 10, 1, "Espoo") }
                .data
                .flatMap { it.messages.mapNotNull { m -> m.readAt } }
                .size
        )

        // when employee gets a reply
        replyToThread(
            thread2Id,
            person1Account,
            setOf(employee1Account),
            "Just replying here",
            thread.messages.last().id,
            now = sendTime.plusSeconds(3)
        )

        // then employee sees the thread
        val employeeResult =
            db.read { it.getReceivedThreads(readTime, employee1Account, 10, 1, "Espoo") }
        assertEquals(1, employeeResult.data.size)
        assertEquals("Newest thread", employeeResult.data[0].title)
        assertEquals(2, employeeResult.data[0].messages.size)

        // person 1 is recipient in both threads
        val person1Result = db.read { it.getThreads(readTime, person1Account, 10, 1, "Espoo") }
        assertEquals(2, person1Result.data.size)

        val newestThread = person1Result.data[0]
        assertEquals(thread2Id, newestThread.id)
        assertEquals("Newest thread", newestThread.title)
        assertEquals(
            listOf(Pair(employee1Account, "Content 2"), Pair(person1Account, "Just replying here")),
            newestThread.messages.map { Pair(it.sender.id, it.content) }
        )
        assertEquals(employeeResult.data[0], newestThread)

        val oldestThread = person1Result.data[1]
        assertEquals(thread1Id, oldestThread.id)
        assertNotNull(oldestThread.messages.find { it.content == "Content" }?.readAt)
        assertNull(oldestThread.messages.find { it.content == "Just replying here" }?.readAt)

        // person 2 is recipient in the oldest thread only
        val person2Result = db.read { it.getThreads(readTime, person2Account, 10, 1, "Espoo") }
        assertEquals(1, person2Result.data.size)
        assertEquals(oldestThread.id, person2Result.data[0].id)
        assertEquals(0, person2Result.data.flatMap { it.messages }.mapNotNull { it.readAt }.size)

        // employee 2 is participating with himself
        val employee2Result =
            db.read { it.getReceivedThreads(readTime, employee2Account, 10, 1, "Espoo") }
        assertEquals(1, employee2Result.data.size)
        assertEquals(1, employee2Result.data[0].messages.size)
        assertEquals(employee2Account, employee2Result.data[0].messages[0].sender.id)
        assertEquals("Alone", employee2Result.data[0].messages[0].content)
    }

    @Test
    fun `received messages can be paged`() {
        val (employee1Account, person1Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id)
                )
            }

        createThread("t1", "c1", employee1Account, listOf(person1Account))
        createThread("t2", "c2", employee1Account, listOf(person1Account))

        val messages = db.read { it.getThreads(readTime, person1Account, 10, 1, "Espoo") }
        assertEquals(2, messages.total)
        assertEquals(2, messages.data.size)
        assertEquals(setOf("t1", "t2"), messages.data.map { it.title }.toSet())

        val (page1, page2) =
            db.read {
                listOf(
                    it.getThreads(readTime, person1Account, 1, 1, "Espoo"),
                    it.getThreads(readTime, person1Account, 1, 2, "Espoo")
                )
            }
        assertEquals(2, page1.total)
        assertEquals(2, page1.pages)
        assertEquals(1, page1.data.size)
        assertEquals(messages.data[0], page1.data[0])

        assertEquals(2, page2.total)
        assertEquals(2, page2.pages)
        assertEquals(1, page2.data.size)
        assertEquals(messages.data[1], page2.data[0])
    }

    @Test
    fun `sent messages`() {
        val (employee1Account, person1Account, person2Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id),
                    it.getCitizenMessageAccount(person2Id)
                )
            }

        // when two threads are created
        createThread(
            "thread 1",
            "content 1",
            employee1Account,
            listOf(person1Account, person2Account),
            sendTime
        )
        createThread(
            "thread 2",
            "content 2",
            employee1Account,
            listOf(person1Account),
            sendTime.plusSeconds(1)
        )

        // then sent messages are returned for sender id
        val firstPage = db.read { it.getMessagesSentByAccount(employee1Account, 1, 1) }
        assertEquals(2, firstPage.total)
        assertEquals(2, firstPage.pages)
        assertEquals(1, firstPage.data.size)

        val newestMessage = firstPage.data[0]
        assertEquals("content 2", newestMessage.content)
        assertEquals("thread 2", newestMessage.threadTitle)
        assertEquals(setOf(person1Account), newestMessage.recipients.map { it.id }.toSet())

        val secondPage = db.read { it.getMessagesSentByAccount(employee1Account, 1, 2) }
        assertEquals(2, secondPage.total)
        assertEquals(2, secondPage.pages)
        assertEquals(1, secondPage.data.size)

        val oldestMessage = secondPage.data[0]
        assertEquals("content 1", oldestMessage.content)
        assertEquals("thread 1", oldestMessage.threadTitle)
        assertEquals(
            setOf(person1Account, person2Account),
            oldestMessage.recipients.map { it.id }.toSet()
        )

        // then fetching sent messages by recipient ids does not return the messages
        assertEquals(0, db.read { it.getMessagesSentByAccount(person1Account, 1, 1) }.total)
    }

    @Test
    fun `message participants by messageId`() {
        val (employee1Account, person1Account, person2Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id),
                    it.getCitizenMessageAccount(person2Id)
                )
            }

        val threadId =
            createThread(
                "Hello",
                "Content",
                employee1Account,
                listOf(person1Account, person2Account)
            )

        val participants =
            db.read {
                val messageId =
                    it.createQuery("SELECT id FROM message WHERE thread_id = :threadId")
                        .bind("threadId", threadId)
                        .mapTo<MessageId>()
                        .one()
                it.getThreadByMessageId(messageId)
            }
        assertEquals(
            ThreadWithParticipants(
                threadId = threadId,
                type = MessageType.MESSAGE,
                isCopy = false,
                senders = setOf(employee1Account),
                recipients = setOf(person1Account, person2Account)
            ),
            participants
        )

        val participants2 =
            db.transaction { tx ->
                val contentId = tx.insertMessageContent("foo", person2Account)
                val messageId =
                    tx.insertMessage(
                        RealEvakaClock().now(),
                        contentId = contentId,
                        threadId = threadId,
                        sender = person2Account,
                        recipientNames = tx.getAccountNames(setOf(employee1Account)),
                        municipalAccountName = "Espoo"
                    )
                tx.insertRecipients(setOf(employee1Account), messageId)
                tx.getThreadByMessageId(messageId)
            }
        assertEquals(
            ThreadWithParticipants(
                threadId = threadId,
                type = MessageType.MESSAGE,
                isCopy = false,
                senders = setOf(employee1Account, person2Account),
                recipients = setOf(person1Account, person2Account, employee1Account)
            ),
            participants2
        )
    }

    @Test
    fun `query citizen receivers`() {
        val placementId: UUID = UUID.randomUUID()
        val group1Id: UUID = UUID.randomUUID()
        val group2Id: UUID = UUID.randomUUID()

        val today = LocalDate.now()
        val startDate = today.minusDays(30)
        val endDateGroup1 = today.plusDays(15)
        val startDateGroup2 = today.plusDays(16)
        val endDate = today.plusDays(30)

        db.transaction { tx ->
            // When there is a daycare with two groups and employee1 as the supervisor
            listOf(employee1Id, employee2Id).forEach { tx.upsertEmployeeMessageAccount(it) }
            tx.insertTestCareArea(testArea)
            tx.insertTestDaycare(
                DevDaycare(
                    areaId = testArea.id,
                    id = testDaycare.id,
                    name = testDaycare.name,
                    language = Language.fi,
                    enabledPilotFeatures = setOf(PilotFeature.MESSAGING)
                )
            )
            tx.insertDaycareAclRow(
                daycareId = testDaycare.id,
                employeeId = employee1Id,
                role = UserRole.UNIT_SUPERVISOR
            )
            tx.insertTestDaycareGroup(
                DevDaycareGroup(
                    id = GroupId(group1Id),
                    daycareId = testDaycare.id,
                    name = "Testiläiset"
                )
            )
            tx.insertTestDaycareGroup(
                DevDaycareGroup(
                    id = GroupId(group2Id),
                    daycareId = testDaycare.id,
                    name = "Testiläiset 2"
                )
            )
            listOf(group1Id, group2Id).map { tx.createDaycareGroupMessageAccount(GroupId(it)) }

            // and person1 has a child who is placed into a group
            tx.insertTestPerson(
                DevPerson(id = testChild_1.id, firstName = "Firstname", lastName = "Test Child")
            )
            tx.insertTestChild(DevChild(id = testChild_1.id))
            tx.insertTestParentship(
                id = ParentshipId(UUID.randomUUID()),
                childId = testChild_1.id,
                headOfChild = person1Id,
                startDate = startDate,
                endDate = endDate
            )
            tx.insertGuardian(guardianId = person1Id, childId = testChild_1.id)
            tx.insertTestPlacement(
                id = PlacementId(placementId),
                childId = testChild_1.id,
                unitId = testDaycare.id,
                type = PlacementType.DAYCARE,
                startDate = startDate,
                endDate = endDate
            )
            tx.insertTestDaycareGroupPlacement(
                id = GroupPlacementId(UUID.randomUUID()),
                daycarePlacementId = PlacementId(placementId),
                groupId = GroupId(group1Id),
                startDate = startDate,
                endDate = endDateGroup1
            )
            tx.insertTestDaycareGroupPlacement(
                id = GroupPlacementId(UUID.randomUUID()),
                daycarePlacementId = PlacementId(placementId),
                groupId = GroupId(group2Id),
                startDate = startDateGroup2,
                endDate = endDate
            )
        }

        val (person1Account, group1Account, group2Account) =
            db.read {
                listOf(
                    it.getCitizenMessageAccount(person1Id),
                    it.getDaycareGroupMessageAccount(GroupId(group1Id)),
                    it.getDaycareGroupMessageAccount(GroupId(group2Id))
                )
            }
        val supervisorPersonalAccount =
            db.read {
                    it.getEmployeeMessageAccountIds(
                        accessControl.requireAuthorizationFilter(
                            it,
                            AuthenticatedUser.Employee(employee1Id, emptySet()),
                            clock,
                            Action.MessageAccount.ACCESS
                        )
                    )
                }
                .first { it != group1Account && it != group2Account }

        // when we get the receivers for the citizen person1
        val receivers = db.read { it.getCitizenReceivers(LocalDate.now(), person1Account).keys }

        assertEquals(
            setOf(
                MessageAccount(group1Account, "Testiläiset", AccountType.GROUP),
                MessageAccount(
                    supervisorPersonalAccount,
                    "Employee Firstname",
                    AccountType.PERSONAL
                )
            ),
            receivers
        )
    }

    @Test
    fun `query citizen receivers when the citizen is on a blocklist`() {
        val placementId: UUID = UUID.randomUUID()
        val group1Id: UUID = UUID.randomUUID()
        val startDate = LocalDate.now().minusDays(30)
        val endDate = LocalDate.now().plusDays(30)
        db.transaction { tx ->
            // When there is a daycare with a group and employee1 as the supervisor
            listOf(employee1Id, employee2Id).forEach { tx.upsertEmployeeMessageAccount(it) }
            tx.insertTestCareArea(testArea)
            tx.insertTestDaycare(
                DevDaycare(
                    areaId = testArea.id,
                    id = testDaycare.id,
                    name = testDaycare.name,
                    language = Language.fi
                )
            )
            tx.insertDaycareAclRow(
                daycareId = testDaycare.id,
                employeeId = employee1Id,
                role = UserRole.UNIT_SUPERVISOR
            )
            tx.insertTestDaycareGroup(
                DevDaycareGroup(
                    id = GroupId(group1Id),
                    daycareId = testDaycare.id,
                    name = "Testiläiset"
                )
            )
            tx.createDaycareGroupMessageAccount(GroupId(group1Id))

            // and person1 has a child who is placed into the group
            tx.insertTestPerson(
                DevPerson(id = testChild_1.id, firstName = "Firstname", lastName = "Test Child")
            )
            tx.insertTestChild(DevChild(id = testChild_1.id))
            tx.insertTestParentship(
                id = ParentshipId(UUID.randomUUID()),
                childId = testChild_1.id,
                headOfChild = person1Id,
                startDate = startDate,
                endDate = endDate
            )
            tx.insertGuardian(guardianId = person1Id, childId = testChild_1.id)
            tx.insertTestPlacement(
                id = PlacementId(placementId),
                childId = testChild_1.id,
                unitId = testDaycare.id,
                type = PlacementType.DAYCARE,
                startDate = startDate,
                endDate = endDate
            )
            tx.insertTestDaycareGroupPlacement(
                id = GroupPlacementId(UUID.randomUUID()),
                daycarePlacementId = PlacementId(placementId),
                groupId = GroupId(group1Id),
                startDate = startDate,
                endDate = endDate
            )

            // and person1 is a blocked receiver
            tx.addToBlocklist(testChild_1.id, person1Id)
        }

        val person1Account = db.read { it.getCitizenMessageAccount(person1Id) }
        // when we get the receivers for the citizen person1
        val receivers = db.read { it.getCitizenReceivers(LocalDate.now(), person1Account).keys }

        // the result is empty
        assertEquals(setOf(), receivers.map { it.id }.toSet())
    }

    @Test
    fun `unread messages and marking messages read`() {
        // given
        val (employee1Account, person1Account, person2Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id),
                    it.getCitizenMessageAccount(person2Id)
                )
            }
        val thread1 =
            createThread(
                "Title",
                "Content",
                person1Account,
                listOf(employee1Account, person2Account)
            )

        // then unread count is zero for sender and one for recipients
        assertEquals(
            0,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person1Account)).first().unreadCount
            }
        )
        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(employee1Account)).first().unreadCount
            }
        )
        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person2Account)).first().unreadCount
            }
        )

        // when employee reads the message
        db.transaction { it.markThreadRead(RealEvakaClock(), employee1Account, thread1) }

        // then the thread does not count towards unread messages
        assertEquals(
            0,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person1Account)).first().unreadCount
            }
        )
        assertEquals(
            0,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(employee1Account)).first().unreadCount
            }
        )
        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person2Account)).first().unreadCount
            }
        )

        // when a new thread is created
        val thread2 =
            createThread(
                "Title",
                "Content",
                employee1Account,
                listOf(person1Account, person2Account)
            )

        // then unread counts are bumped by one for recipients
        assertEquals(
            0,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(employee1Account)).first().unreadCount
            }
        )
        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person1Account)).first().unreadCount
            }
        )
        assertEquals(
            2,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person2Account)).first().unreadCount
            }
        )

        // when person two reads a thread
        db.transaction { it.markThreadRead(RealEvakaClock(), person2Account, thread2) }

        // then unread count goes down by one
        assertEquals(
            0,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(employee1Account)).first().unreadCount
            }
        )
        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person1Account)).first().unreadCount
            }
        )
        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person2Account)).first().unreadCount
            }
        )
    }

    @Test
    fun `a thread can be archived`() {
        val (employeeAccount, person1Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id)
                )
            }

        val content = "Content"
        val title = "Hello"
        val threadId = createThread(title, content, employeeAccount, listOf(person1Account))

        assertEquals(
            1,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person1Account)).first().unreadCount
            }
        )

        db.transaction { tx -> tx.archiveThread(person1Account, threadId) }

        assertEquals(
            0,
            db.read {
                it.getUnreadMessagesCounts(readTime, setOf(person1Account)).first().unreadCount
            }
        )

        assertEquals(
            1,
            db.read {
                val archiveFolderId = it.getArchiveFolderId(person1Account)
                it.getReceivedThreads(readTime, person1Account, 50, 1, "Espoo", archiveFolderId)
                    .total
            }
        )
    }

    @Test
    fun `an archived threads returns to inbox when it receives messages`() {
        val (employeeAccount, person1Account) =
            db.read {
                listOf(
                    it.getEmployeeMessageAccountIds(
                            accessControl.requireAuthorizationFilter(
                                it,
                                AuthenticatedUser.Employee(employee1Id, emptySet()),
                                clock,
                                Action.MessageAccount.ACCESS
                            )
                        )
                        .first(),
                    it.getCitizenMessageAccount(person1Id)
                )
            }

        val content = "Content"
        val title = "Hello"
        val threadId = createThread(title, content, employeeAccount, listOf(person1Account))
        db.transaction { tx -> tx.archiveThread(person1Account, threadId) }
        assertEquals(
            1,
            db.read {
                val archiveFolderId = it.getArchiveFolderId(person1Account)
                it.getReceivedThreads(readTime, person1Account, 50, 1, "Espoo", archiveFolderId)
                    .total
            }
        )

        replyToThread(threadId, employeeAccount, setOf(person1Account), "Reply")

        assertEquals(
            1,
            db.read { it.getReceivedThreads(readTime, person1Account, 50, 1, "Espoo", null).total }
        )
        assertEquals(
            0,
            db.read {
                val archiveFolderId = it.getArchiveFolderId(person1Account)
                it.getReceivedThreads(readTime, person1Account, 50, 1, "Espoo", archiveFolderId)
                    .total
            }
        )
    }

    // TODO: Remove this function, creating threads should be MessageService's job
    private fun createThread(
        title: String,
        content: String,
        sender: MessageAccountId,
        recipientAccounts: List<MessageAccountId>,
        now: HelsinkiDateTime = sendTime
    ): MessageThreadId {
        return db.transaction { tx ->
            val contentId = tx.insertMessageContent(content, sender)
            val threadId =
                tx.insertThread(MessageType.MESSAGE, title, urgent = false, isCopy = false)
            val messageId =
                tx.insertMessage(
                    now,
                    contentId = contentId,
                    threadId = threadId,
                    sender = sender,
                    recipientNames = tx.getAccountNames(recipientAccounts.toSet()),
                    municipalAccountName = "Espoo"
                )
            tx.insertRecipients(recipientAccounts.toSet(), messageId)
            tx.upsertThreadParticipants(threadId, sender, recipientAccounts.toSet(), now)
            threadId
        }
    }

    // TODO: Remove this function; replying to a thread should be MessageService's job
    private fun replyToThread(
        threadId: MessageThreadId,
        sender: MessageAccountId,
        recipients: Set<MessageAccountId>,
        content: String,
        repliesToMessageId: MessageId? = null,
        now: HelsinkiDateTime = sendTime
    ) {
        db.transaction {
            val contentId = it.insertMessageContent(content = content, sender = sender)
            val messageId =
                it.insertMessage(
                    now = now,
                    contentId = contentId,
                    threadId = threadId,
                    sender = sender,
                    repliesToMessageId = repliesToMessageId,
                    recipientNames = listOf(),
                    municipalAccountName = "Espoo"
                )
            it.insertRecipients(recipientAccountIds = recipients, messageId = messageId)
            it.upsertThreadParticipants(threadId, sender, recipients, now)
        }
    }
}
