// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka.pdfgen

import fi.espoo.evaka.application.ServiceNeed
import fi.espoo.evaka.daycare.domain.ProviderType
import fi.espoo.evaka.daycare.service.DaycareManager
import fi.espoo.evaka.decision.Decision
import fi.espoo.evaka.decision.DecisionStatus
import fi.espoo.evaka.decision.DecisionType
import fi.espoo.evaka.decision.DecisionUnit
import fi.espoo.evaka.decision.createDecisionPdf
import fi.espoo.evaka.identity.ExternalIdentifier
import fi.espoo.evaka.invoicing.service.DocumentLang
import fi.espoo.evaka.pis.service.PersonDTO
import fi.espoo.evaka.setting.SettingType
import fi.espoo.evaka.shared.ApplicationId
import fi.espoo.evaka.shared.ChildId
import fi.espoo.evaka.shared.DaycareId
import fi.espoo.evaka.shared.DecisionId
import fi.espoo.evaka.shared.config.PDFConfig
import fi.espoo.evaka.shared.message.EvakaMessageProvider
import fi.espoo.evaka.shared.message.IMessageProvider
import fi.espoo.evaka.shared.template.EvakaTemplateProvider
import fi.espoo.evaka.shared.template.ITemplateProvider
import fi.espoo.evaka.test.validPreschoolApplication
import fi.espoo.evaka.testAdult_1
import fi.espoo.evaka.testChild_1
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertNotNull
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

val logger = KotlinLogging.logger {}

private val application = validPreschoolApplication
private val transferApplication = application.copy(transferApplication = true)

private val daycareTransferDecision =
    createValidDecision(applicationId = transferApplication.id, type = DecisionType.DAYCARE)
private val daycareDecision =
    createValidDecision(applicationId = application.id, type = DecisionType.DAYCARE)
private val preschoolDaycareDecision =
    createValidDecision(applicationId = application.id, type = DecisionType.PRESCHOOL_DAYCARE)
private val daycareDecisionPartTime =
    createValidDecision(applicationId = application.id, type = DecisionType.DAYCARE_PART_TIME)
private val preschoolDecision =
    createValidDecision(applicationId = application.id, type = DecisionType.PRESCHOOL)
private val preparatoryDecision =
    createValidDecision(applicationId = application.id, type = DecisionType.PREPARATORY_EDUCATION)
private val clubDecision =
    createValidDecision(applicationId = application.id, type = DecisionType.CLUB)

private val voucherDecision =
    daycareDecision.copy(
        endDate = LocalDate.of(2019, 7, 31),
        unit =
            DecisionUnit(
                DaycareId(UUID.randomUUID()),
                "Suomenniemen palvelusetelipäiväkoti",
                "Suomenniemen palvelusetelipäiväkoti",
                "Suomenniemen palvelusetelipäiväkodin esiopetus",
                "Pirkko Sanelma Ullanlinna",
                "Hyväntoivonniementie 13 B",
                "02200",
                "ESPOO",
                "+35850 1234564",
                "Suomenniemen palvelusetelipäiväkodin asiakaspalvelu",
                "Kartanonkujanpää 565, 02210 Espoo",
                providerType = ProviderType.PRIVATE_SERVICE_VOUCHER
            )
    )

private val settings = mapOf<SettingType, String>()
private val child =
    PersonDTO(
        testChild_1.id,
        null,
        ExternalIdentifier.SSN.getInstance(testChild_1.ssn!!),
        false,
        "Kullervo Kyöstinpoika",
        "Pöysti",
        "",
        null,
        "",
        "",
        null,
        testChild_1.dateOfBirth,
        null,
        "Kuusikallionrinne 26 A 4",
        "02270",
        "Espoo",
        ""
    )
private val guardian =
    PersonDTO(
        testAdult_1.id,
        null,
        ExternalIdentifier.SSN.getInstance(testAdult_1.ssn!!),
        false,
        "Kyösti Taavetinpoika",
        "Pöysti",
        "",
        "kyostipoysti@example.com",
        "+358914822",
        "+358914829",
        null,
        testAdult_1.dateOfBirth,
        null,
        "Kuusikallionrinne 26 A 4",
        "02270",
        "Espoo",
        ""
    )
private val manager =
    DaycareManager("Pirkko Päiväkodinjohtaja", "pirkko.paivakodinjohtaja@example.com", "0401231234")

@TestConfiguration
class PdfGeneratorTestConfiguration {
    @Bean fun messageProvider(): IMessageProvider = EvakaMessageProvider()

    @Bean fun templateProvider(): ITemplateProvider = EvakaTemplateProvider()
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [PdfGeneratorTestConfiguration::class, PDFConfig::class, PdfGenerator::class]
)
class PdfGeneratorTest {
    @Autowired lateinit var pdfGenerator: PdfGenerator

    @Test
    fun createFinnishPDFs() {
        createPDF(daycareTransferDecision, true, DocumentLang.FI)
        createPDF(daycareDecision, false, DocumentLang.FI)
        createPDF(daycareDecisionPartTime, false, DocumentLang.FI)
        createPDF(preschoolDaycareDecision, false, DocumentLang.FI)
        createPDF(preschoolDecision, false, DocumentLang.FI)
        createPDF(preparatoryDecision, false, DocumentLang.FI)
        createPDF(voucherDecision, false, DocumentLang.FI)
        createPDF(clubDecision, false, DocumentLang.FI)
    }

    @Test
    fun createSwedishPDFs() {
        createPDF(daycareTransferDecision, true, DocumentLang.SV)
        createPDF(daycareDecision, false, DocumentLang.SV)
        createPDF(daycareDecisionPartTime, false, DocumentLang.SV)
        createPDF(preschoolDaycareDecision, false, DocumentLang.SV)
        createPDF(preschoolDecision, false, DocumentLang.SV)
        createPDF(preparatoryDecision, false, DocumentLang.SV)
        createPDF(voucherDecision, false, DocumentLang.SV)
        createPDF(clubDecision, false, DocumentLang.SV)
    }

    private fun createPDF(
        decision: Decision,
        isTransferApplication: Boolean,
        lang: DocumentLang,
        serviceNeed: ServiceNeed? = null
    ) {
        val decisionPdfByteArray =
            createDecisionPdf(
                EvakaMessageProvider(),
                EvakaTemplateProvider(),
                pdfGenerator,
                settings,
                decision,
                guardian,
                child,
                isTransferApplication,
                serviceNeed,
                lang,
                manager
            )

        val file = File.createTempFile("decision_", ".pdf")
        assertNotNull(decisionPdfByteArray)

        FileOutputStream(file).use { it.write(decisionPdfByteArray) }

        logger.debug {
            "Generated $lang ${decision.type} (${decision.unit.providerType}${if (isTransferApplication) ", transfer application" else ""}) decision PDF to ${file.absolutePath}"
        }
    }
}

fun createValidDecision(
    id: DecisionId = DecisionId(UUID.randomUUID()),
    createdBy: String = "John Doe",
    type: DecisionType = DecisionType.DAYCARE,
    startDate: LocalDate = LocalDate.of(2019, 1, 1),
    endDate: LocalDate = LocalDate.of(2019, 12, 31),
    unit: DecisionUnit =
        DecisionUnit(
            DaycareId(UUID.randomUUID()),
            "Kuusenkerkän päiväkoti",
            "Kuusenkerkän päiväkoti",
            "Kuusenkerkän päiväkodin esiopetus",
            "Pirkko Päiväkodinjohtaja",
            "Kuusernkerkänpolku 123",
            "02200",
            "ESPOO",
            "+35850 1234564",
            "Varhaiskasvatuksen palveluohjaus",
            "Kamreerintie 2, 02200 Espoo",
            providerType = ProviderType.MUNICIPAL
        ),
    applicationId: ApplicationId = ApplicationId(UUID.randomUUID()),
    childId: ChildId = ChildId(UUID.randomUUID()),
    documentKey: String? = null,
    otherGuardianDocumentKey: String? = null,
    decisionNumber: Long = 123,
    sentDate: LocalDate = LocalDate.now(),
    status: DecisionStatus = DecisionStatus.ACCEPTED,
    resolved: LocalDate? = null
): Decision {
    return Decision(
        id = id,
        createdBy = createdBy,
        type = type,
        startDate = startDate,
        endDate = endDate,
        unit = unit,
        applicationId = applicationId,
        childId = childId,
        documentKey = documentKey,
        otherGuardianDocumentKey = otherGuardianDocumentKey,
        decisionNumber = decisionNumber,
        sentDate = sentDate,
        status = status,
        childName = "Test Child",
        requestedStartDate = startDate,
        resolved = resolved,
        resolvedByName = null
    )
}
