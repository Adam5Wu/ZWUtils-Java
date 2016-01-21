/*
 * Copyright (c) 2011 - 2016, Zhenyu Wu, NEC Labs America Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of ZWUtils-Java nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * // @formatter:on
 */

package com.necla.am.zwutils.Debugging;

import java.util.Date;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.necla.am.zwutils.Misc.Misc;


/**
 * Sending email via SMTP server
 *
 * @author Zhenyu Wu
 * @version 0.1 - Dec. 2015: Initial implementation
 * @version 0.1 - Jan. 20 2016: Initial public release
 */
public class SMTPEmail {
	
	InternetAddress FromEmail;
	InternetAddress ToEmail;
	Properties SMTPProps;
	
	public SMTPEmail(String SMTPServer, int SMTPPort, String fromEmail, String toEmail) {
		this(SMTPServer, SMTPPort, fromEmail, null, toEmail, null);
	}
	
	public SMTPEmail(String SMTPServer, int SMTPPort, String fromEmail, String fromName,
			String toEmail, String toName) {
		try {
			FromEmail = new InternetAddress(fromEmail, fromName);
			ToEmail = new InternetAddress(toEmail, toName);
		} catch (Throwable e) {
			Misc.CascadeThrow(e);
		}
		
		SMTPProps = new Properties();
		SMTPProps.put("mail.smtp.host", SMTPServer); //$NON-NLS-1$
		SMTPProps.put("mail.smtp.port", String.valueOf(SMTPPort)); //$NON-NLS-1$
	}
	
	// Adopted from
	// http://www.journaldev.com/2532/java-program-to-send-email-using-smtp-gmail-tls-ssl-attachment-image-example
	public void Send(String subject, String body) {
		try {
			Session session = Session.getInstance(SMTPProps);
			
			MimeMessage msg = new MimeMessage(session);
			// set message headers
			msg.addHeader("Content-type", "text/HTML; charset=UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
			msg.addHeader("format", "flowed"); //$NON-NLS-1$ //$NON-NLS-2$
			msg.addHeader("Content-Transfer-Encoding", "8bit"); //$NON-NLS-1$ //$NON-NLS-2$
			
			msg.setFrom(FromEmail);
			msg.setReplyTo(Misc.wrap(FromEmail));
			msg.setSubject(subject, "UTF-8"); //$NON-NLS-1$
			msg.setText(body, "UTF-8"); //$NON-NLS-1$
			msg.setSentDate(new Date());
			msg.setRecipients(Message.RecipientType.TO, Misc.wrap(ToEmail));
			
			Transport.send(msg);
		} catch (Exception e) {
			Misc.CascadeThrow(e);
		}
	}
	
}
