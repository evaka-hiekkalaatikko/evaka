// SPDX-FileCopyrightText: 2017-2020 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

package fi.espoo.evaka

import com.fasterxml.jackson.databind.ObjectMapper
import fi.espoo.evaka.invoicing.integration.InvoiceIntegrationClient
import fi.espoo.evaka.shared.job.DefaultJobSchedule
import fi.espoo.evaka.shared.job.JobSchedule
import fi.espoo.evaka.shared.message.EvakaMessageProvider
import fi.espoo.evaka.shared.message.IMessageProvider
import fi.espoo.evaka.shared.template.EvakaTemplateProvider
import fi.espoo.evaka.shared.template.ITemplateProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment

@Configuration
@Profile("espoo_evaka")
class EspooConfig {
    @Bean
    fun espooEnv(env: Environment) = EspooEnv.fromEnvironment(env)

    @Bean
    fun invoiceIntegrationClient(env: EspooEnv, invoiceEnv: ObjectProvider<EspooInvoiceEnv>, objectMapper: ObjectMapper): InvoiceIntegrationClient =
        when (env.invoiceEnabled) {
            true -> InvoiceIntegrationClient.Client(invoiceEnv.getObject(), objectMapper)
            false -> InvoiceIntegrationClient.MockClient(objectMapper)
        }

    @Bean
    @Lazy
    fun espooInvoiceEnv(env: Environment) = EspooInvoiceEnv.fromEnvironment(env)

    @Bean
    fun messageProvider(): IMessageProvider = EvakaMessageProvider()

    @Bean
    fun templateProvider(): ITemplateProvider = EvakaTemplateProvider()

    @Bean
    fun jobSchedule(env: Environment): JobSchedule = DefaultJobSchedule.fromEnvironment(env)
}

data class EspooEnv(val invoiceEnabled: Boolean) {
    companion object {
        fun fromEnvironment(env: Environment): EspooEnv = EspooEnv(
            invoiceEnabled = env.lookup("espoo.integration.invoice.enabled", "fi.espoo.integration.invoice.enabled") ?: true
        )
    }
}

data class EspooInvoiceEnv(
    val url: String,
    val username: String,
    val password: Sensitive<String>
) {
    companion object {
        fun fromEnvironment(env: Environment) = EspooInvoiceEnv(
            url = env.lookup("espoo.integration.invoice.url", "fi.espoo.integration.invoice.url"),
            username = env.lookup("espoo.integration.invoice.username", "fi.espoo.integration.invoice.username"),
            password = Sensitive(env.lookup("espoo.integration.invoice.password", "fi.espoo.integration.invoice.password"))
        )
    }
}
