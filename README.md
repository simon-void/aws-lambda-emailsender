# aws-lambda-emailsender

AWS doesn't support Kotlin directly but it does support a jar-file containing Java8-bytecode.
So this Kotlin project is configured to produce just that.

This Lambda allows to send emails to receivers only known by a token to the sender.
This is for example useful to allow a (potentially Cross-Origin) website to
send emails to the website's maintainer via a POST-request to the API Gateway
without leaking their email address to people tech-savy enough to inspect the
sourcecode of the website.

### Environment Variables that must be set:

+ `verified_SES_email`: an email like `example@gmail.com`
+ `receiver_email_by_name_csv`: a comma separated list of {name}={email} like `person1=guy@gmail.com, dojo=dojo.owner@yahoo.com`

### Parameters that are assumed:
+ `subject`: String - the subject of the email to send
+ `message`: String - the message of the email to send
+ `senderEmail`: String? - the (optional) email address of the sender
+ `receiverName`: String - must be a key in the map defined by `receiver_email_by_name_csv`. The email will be send to the email address linked to that name.