package example.api.v1;

import java.util.List;

public class Email {
    private String recipient;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String htmlBody;
    private String textBody;
    private String replyTo;

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    Email textBody(String textBody) {
        setTextBody(textBody);
        return this;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public Email recipient(String recipient) {
        setRecipient(recipient);
        return this;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public Email cc(List<String> cc) {
        setCc(cc);
        return this;
    }

    public List<String> getBcc() {
        return bcc;
    }

    public void setBcc(List<String> bcc) {
        this.bcc = bcc;
    }

    public Email bcc(List<String> bcc) {
        setBcc(bcc);
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Email subject(String subject) {
        setSubject(subject);
        return this;
    }

    public String getHtmlBody() {
        return htmlBody;
    }

    public void setHtmlBody(String htmlBody) {
        this.htmlBody = htmlBody;
    }

    public Email htmlBody(String htmlBody) {
        setHtmlBody(htmlBody);
        return this;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public Email replyTo(String replyTo) {
        setReplyTo(replyTo);
        return this;
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
            sb.append("HTML Body: ");
            sb.append(getHtmlBody());
        }
        if ( getTextBody() != null ) {
            sb.append("Text Body: ");
            sb.append(getTextBody());
        }
        return sb.toString();
    }
}
