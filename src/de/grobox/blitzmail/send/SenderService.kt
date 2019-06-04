package de.grobox.blitzmail.send

import android.app.IntentService
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Handler
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import de.grobox.blitzmail.BuildConfig
import de.grobox.blitzmail.MailStorage
import de.grobox.blitzmail.R
import de.grobox.blitzmail.notification.MailNotificationManager
import de.grobox.blitzmail.notification.NOTIFICATION_ID_SENDING
import de.grobox.blitzmail.notification.getMailNotificationManager
import de.grobox.blitzmail.preferences.getProperties
import org.json.JSONObject

const val MAIL = "mail"
const val MAIL_ID = "id"
const val MAIL_BODY = "body"
const val MAIL_SUBJECT = "subject"
const val MAIL_CC = "cc"
const val MAIL_DATE = "date"
const val MAIL_ATTACHMENTS = "attachments"

class SenderService : IntentService("SenderService") {

    private lateinit var mailNotificationManager: MailNotificationManager
    private lateinit var connectivityManager: ConnectivityManager

    override fun onCreate() {
        super.onCreate()
        mailNotificationManager = getMailNotificationManager(applicationContext)
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        mailNotificationManager.createNotificationChannel();
        startForeground(NOTIFICATION_ID_SENDING, mailNotificationManager.getForegroundNotification());

        intent.getStringExtra(MAIL)?.let {
            // save mail before sending all saved mails
            MailStorage.saveMail(this, JSONObject(it))
        }

        // send all saved mails
        val mails = MailStorage.getMails(this)
        mails.keys().forEach {
            val mail = mails.getJSONObject(it);
            sendMail(mail)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }

    private fun sendMail(mail: JSONObject) {
        val mailId = mail.getInt(MAIL_ID)
        val subject = mail.getString(MAIL_SUBJECT)
        try {
            MailSender(getProperties(applicationContext), mail).sendMail()

            mailNotificationManager.showSuccessNotification(mailId, subject)

            // Everything went fine, so delete mail from local storage
            MailStorage.deleteMail(applicationContext, mailId.toString())
        } catch (e: Exception) {
            e.printStackTrace()

            val networkInfo = connectivityManager.activeNetworkInfo
            if (BuildConfig.PRO && (networkInfo == null || !networkInfo.isConnected)) {
                scheduleSending(0)
                Handler(mainLooper).post {
                    Toast.makeText(applicationContext, getString(R.string.mail_queued), LENGTH_LONG).show()
                }
            } else {
                mailNotificationManager.showErrorNotification(e, mailId.toString())
            }
        }
    }

}
