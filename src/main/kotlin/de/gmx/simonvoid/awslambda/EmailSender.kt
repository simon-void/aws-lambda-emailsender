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
    private val verifiedAWSSenderEmail: String? = System.getenv(VERIFIED_EMAIL_KEY)?.trim()

    /**
     * A comma-separated list of name-to-email
     */
    private val knownReceiver: Map<String, String> = System.getenv(RECEIVER_EMAIL_BY_NAME_KEY)?.let {emailByNameCSV: String ->
        emailByNameCSV.split(",").mapNotNull {
            val emailAndName: List<String> = it.trim().split('=')
            if(emailAndName.size==2) {
                Pair(emailAndName[0].trim(), emailAndName[1].trim())
            } else {
                null
            }
        }.toMap()
    } ?: emptyMap()

    override fun handleRequest(request: Request, context: Context): Response? {
        fun String?.blankToNull() = this?.trim()?.let { if (it.isEmpty()) null else it }
        val subject: String? = request.subject.blankToNull()
        val message: String? = request.message.blankToNull()
        val senderEmail: String? = request.senderEmail.blankToNull()
        val receiverName: String? = request.receiverName.blankToNull()
        val receiverEmail: String? = receiverName?.let { knownReceiver[it] }

        if(verifiedAWSSenderEmail==null || knownReceiver.isEmpty()) {
            return getConfigErrorResponse(context)
        }

        if(subject==null || message==null || receiverName==null || receiverEmail==null) {
            return getParameterErrorResponse(subject, message, receiverName, receiverEmail)
        }

        return try {
            sendEmail(subject, message, receiverName, receiverEmail, senderEmail)
            Response(wasSuccessful = true)
        }catch(e: AmazonSimpleEmailServiceException) {
            Response(
                    wasSuccessful = false,
                    errorMessage = "failed to send email because: ${e.localizedMessage}"
            )
        }
    }

    /**
     * @param senderEmail is nullable because it's considered optional (the user of the contact form doesn't need to provide their email)
     */
    private fun sendEmail(subject: String, message: String, receiverName: String, receiverEmail: String, senderEmail: String?) {
        fun String.toUtf8Content(): Content = Content(this).withCharset("UTF-8")

        val request = SendEmailRequest(
                verifiedAWSSenderEmail,
                Destination(listOf(receiverEmail)),
                Message(
                        "$receiverName contact msg: $subject".toUtf8Content(),
                        Body("senderEmail: $senderEmail\n\nmessage: $message".toUtf8Content())
                )
        )

        emailClient.sendEmail(request)
    }

    private fun getParameterErrorResponse(subject: String?, message: String?, receiverName: String?, receiverEmail: String?): Response {
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

        return Response(
                wasSuccessful = false,
                errorMessage = if (errorList.size == 1) {
                    errorList[0]
                } else {
                    "problems: ${errorList.joinToString()}"
                }
        )
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
        return Response(
                wasSuccessful = false,
                errorMessage = "AWS LAMBDA ${context.functionName} misconfigured. ${errorList.joinToString()}"
        )
    }
}


class Request {
    var subject: String? = null
    var message: String? = null
    var senderEmail: String? = null
    var receiverName: String? = null
}

data class Response(
        val wasSuccessful: Boolean,
        val errorMessage: String = ""
)
