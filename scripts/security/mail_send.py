import os, sys, argparse, smtplib
from email.message import EmailMessage

parser = argparse.ArgumentParser()
parser.add_argument('--smtp-server', help=' : Please set the smtp server')
parser.add_argument('--smtp-port', help=' : Please set the smtp port')
parser.add_argument('--from-email-addr', help=' : Please set the from email addr')
parser.add_argument('--from-email-pw', help=' : Please set the from email pw')
parser.add_argument('--to-email-addr', help=' : Please set the to email addr')
parser.add_argument('--subject', help=' : Please set the subject')

args = parser.parse_args()

def main(argv, args) :
    # STMP 서버의 url과 port 번호
    SMTP_SERVER = args.smtp_server
    SMTP_PORT = args.smtp_port

    # 1. SMTP 서버 연결
    smtp = smtplib.SMTP_SSL(SMTP_SERVER, SMTP_PORT)

    FROM_EMAIL_ADDR = args.from_email_addr
    FROM_EMAIL_PASSWORD = args.from_email_pw
    TO_MAIL_ADDR = args.to_email_addr

    # 2. SMTP 서버에 로그인
    smtp.login(FROM_EMAIL_ADDR, FROM_EMAIL_PASSWORD)

    # 3. MIME 형태의 이메일 메세지 작성
    message = EmailMessage()
    message["Subject"] = args.subject
    message["From"] = FROM_EMAIL_ADDR  #보내는 사람의 이메일 계정
    message["To"] = TO_MAIL_ADDR

    # 4. 서버로 메일 보내기
    smtp.send_message(message)

    # 5. 메일을 보내면 서버와의 연결 끊기
    smtp.quit()

if __name__ == '__main__' :
    argv = sys.argv
    main(argv, args)