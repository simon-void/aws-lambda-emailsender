package de.gmx.simonvoid.awslambda

import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model.*
import java.util.*

private const val VERIFIED_EMAIL_KEY = "verified_SES_email"
private const val RECEIVER_EMAIL_BY_NAME_KEY = "receiver_email_by_name_csv"

class EmailSender : RequestHandler<Request, Response> {

    private val emailClient: AmazonSimpleEmailService =
        AmazonSimpleEmailServiceClientBuilder.standard().apply {
            withRegion(Regions.EU_WEST_1)
        }.build()

    /**
     * This email address must be either individually verified with Amazon
     * SES, or from a domain that has been verified with Amazon SES.
     * It is retrieved from the Environment Variables via the key [VERIFIED_EMAIL_KEY]
     */
    private val verifiedAWSSenderEmail: Email? = System.getenv(VERIFIED_EMAIL_KEY)?.trim()?.let { Email.newOrNull(it) }

    /**
     * A comma-separated list of name-to-email
     */
    private val knownReceiver: Map<String, String> =
        System.getenv(RECEIVER_EMAIL_BY_NAME_KEY)?.let { emailByNameCSV: String ->
            emailByNameCSV.split(",").mapNotNull {
                val emailAndName: List<String> = it.trim().split('=')
                if (emailAndName.size == 2) {
                    Pair(emailAndName[0].trim(), emailAndName[1].trim())
                } else {
                    null
                }
            }.toMap()
        } ?: emptyMap()

    override fun handleRequest(request: Request, context: Context): Response? {
        fun String?.blankToNull() = this?.trim()?.let { it.ifEmpty { null } }
        val subject: String? = request.subject.blankToNull()
        val message: String? = request.message.blankToNull()
        val senderEmailOrText: String? = request.senderEmail.blankToNull()
        val receiverName: String? = request.receiverName.blankToNull()
        val receiverEmail: Email? = receiverName?.let { knownReceiver[it] }?.let { Email.newOrNull(it) }

        if (verifiedAWSSenderEmail == null || knownReceiver.isEmpty()) {
            return getConfigErrorResponse(context)
        }

        if (subject == null || message == null || receiverName == null || receiverEmail == null) {
            return getParameterErrorResponse(subject, message, receiverName, receiverEmail)
        }

        return try {
            emailClient.sendEmail(emailRequest(
                verifiedAWSSenderEmail = verifiedAWSSenderEmail,
                receiverEmail = receiverEmail,
                subject = "$receiverName contact msg: $subject",
                body = """
                    sender: $senderEmailOrText
                    
                    message: $message
                    """.trimIndent(),
            ))
            Response.success()
        } catch (e: AmazonSimpleEmailServiceException) {
            Response.failure("failed to send email because: ${e.localizedMessage}")
        }
    }

    private fun emailRequest(
        verifiedAWSSenderEmail: Email,
        receiverEmail: Email,
        subject: String,
        body: String,
    ): SendEmailRequest {
        fun String.toUtf8Content(): Content = Content(this).withCharset("UTF-8")

        return SendEmailRequest(
            verifiedAWSSenderEmail.value,
            Destination(listOf(receiverEmail.value)),
            Message(
                subject.toUtf8Content(),
                Body(body.toUtf8Content()),
            ),
        )
    }

    private fun getParameterErrorResponse(
        subject: String?,
        message: String?,
        receiverName: String?,
        receiverEmail: Email?
    ): Response {
        val errorList: List<String> = LinkedList<String>().apply {
            if (subject == null) {
                add("no subject provided")
            }
            if (message == null) {
                add("no message provided")
            }
            if (receiverName == null) {
                add("no receiver specified (probably a config issue)")
            } else {
                if (receiverEmail == null) {
                    add("no receiver email found for name $receiverName. Known receiver are ${knownReceiver.keys.joinToString()}.")
                }
            }
        }

        return Response.failure("problem(s): ${errorList.joinToString(prefix = "\n- ", separator = ",\n -", postfix = "")}")
    }

    private fun getConfigErrorResponse(context: Context): Response {
        val errorList: List<String> = LinkedList<String>().apply {
            if (verifiedAWSSenderEmail == null) {
                add("No verified email found for key $VERIFIED_EMAIL_KEY")
            }
            if (knownReceiver.isEmpty()) {
                add("No receiver email and name found for key $RECEIVER_EMAIL_BY_NAME_KEY")
            }
        }
        return Response.failure("AWS LAMBDA ${context.functionName} misconfigured. ${errorList.joinToString()}")
    }
}

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private fun isValid(maybeEmail: String): Boolean = maybeEmail.contains('@')
        fun newOrNull(value: String): Email? = if(isValid(value)) Email(value) else null
    }
}


data class Request(
    var subject: String? = null,
    var message: String? = null,
    var senderEmail: String? = null,
    var receiverName: String? = null,
)

// I'd prefer a sealed class, but I'm not sure if AWS would handle those correctly
data class Response(
    val wasSuccessful: Boolean,
    val errorMessage: String = "",
) {
    companion object {
        fun success(): Response = Response(wasSuccessful = true)
        fun failure(errorMessage: String): Response = Response(
            wasSuccessful = false,
            errorMessage = errorMessage,
        )
    }
}
