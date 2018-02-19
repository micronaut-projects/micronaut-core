package example.mail;

import java.util.List;

public class Email {
    private String recipient;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String htmlBody;
    private String replyTo;

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public List<String> getBcc() {
        return bcc;
    }

    public void setBcc(List<String> bcc) {
        this.bcc = bcc;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        if ( getRecipient() != null ) {
            sb.append("Recipient: ");
            sb.append(getRecipient());
        }

        if ( getSubject() != null ) {
            sb.append("Subject: ");
            sb.append(getSubject());
        }

        if ( cc != null ) {
            sb.append("ccs: ");
            for ( String email : cc ) {
                sb.append(email);
            }
        }
        if ( bcc != null ) {
            sb.append("bccs: ");
            for (String email : bcc) {
                sb.append(email);
            }
        }

        if ( getReplyTo() != null ) {
            sb.append("ReplyTo: ");
            sb.append(getReplyTo());
        }

        if ( getHtmlBody() != null ) {
            sb.append("Body: ");
            sb.append(getHtmlBody());
        }
        return sb.toString();
    }
}
