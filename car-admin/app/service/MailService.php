<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Log;

class MailService
{
    public static function available(): bool
    {
        return ConfigService::value('smtp_host') !== ''
            && ConfigService::value('smtp_from_address') !== '';
    }

    public static function recipients(): array
    {
        $raw = trim(ConfigService::value('email_recipients', ''));
        if ($raw === '') return [];
        // 支持逗号、分号、换行（含转义 \n \r \r\n）分割
        $list = preg_split('/[,;\r\n]+/', $raw);
        return array_values(array_unique(array_filter(array_map('trim', $list), fn($v) => $v !== '' && filter_var($v, FILTER_VALIDATE_EMAIL))));
    }

    /**
     * 向所有收件人发送邮件，失败仅记日志不抛异常。
     */
    public static function notify(string $subject, string $htmlBody): void
    {
        if (!self::available()) return;
        $recipients = self::recipients();
        if (!$recipients) return;
        foreach ($recipients as $to) {
            try {
                self::send($to, $subject, $htmlBody);
            } catch (\Throwable $error) {
                Log::warning('email notify failed | to=' . $to . ' | subject=' . $subject . ' | message=' . mb_substr($error->getMessage(), 0, 500));
            }
        }
    }

    /**
     * 发送单封邮件。
     *
     * @throws \RuntimeException 发送失败时抛出
     */
    public static function send(string $to, string $subject, string $htmlBody): bool
    {
        $host = trim(ConfigService::value('smtp_host'));
        $port = (int) ConfigService::value('smtp_port', '465');
        $encryption = strtolower(trim(ConfigService::value('smtp_encryption', 'ssl')));
        $username = ConfigService::value('smtp_username');
        $password = ConfigService::secure('smtp_password');
        $fromAddress = ConfigService::value('smtp_from_address');
        $fromName = ConfigService::value('smtp_from_name', '车辆查询系统');

        if ($host === '' || $fromAddress === '') {
            throw new \RuntimeException('邮件服务未配置完整');
        }

        $errno = 0;
        $errstr = '';
        $scheme = $encryption === 'ssl' ? 'ssl' : 'tcp';
        $connectHost = $scheme . '://' . $host . ':' . $port;
        $timeout = 15;

        $fp = @stream_socket_client($connectHost, $errno, $errstr, $timeout);
        if (!$fp) {
            throw new \RuntimeException('SMTP 连接失败：' . $errstr);
        }
        stream_set_timeout($fp, 15);

        try {
            self::smtpRead($fp, '220');

            self::smtpWrite($fp, 'EHLO ' . $host);
            self::smtpRead($fp, '250');

            if ($encryption === 'tls') {
                self::smtpWrite($fp, 'STARTTLS');
                self::smtpRead($fp, '220');
                if (!stream_socket_enable_crypto($fp, true, STREAM_CRYPTO_METHOD_TLSv1_2_CLIENT | STREAM_CRYPTO_METHOD_TLSv1_3_CLIENT)) {
                    throw new \RuntimeException('STARTTLS 加密握手失败');
                }
                self::smtpWrite($fp, 'EHLO ' . $host);
                self::smtpRead($fp, '250');
            }

            if ($username !== '' && $password !== '') {
                self::smtpWrite($fp, 'AUTH LOGIN');
                self::smtpRead($fp, '334');
                self::smtpWrite($fp, base64_encode($username));
                self::smtpRead($fp, '334');
                self::smtpWrite($fp, base64_encode($password));
                self::smtpRead($fp, '235');
            }

            self::smtpWrite($fp, 'MAIL FROM:<' . $fromAddress . '>');
            self::smtpRead($fp, '250');

            self::smtpWrite($fp, 'RCPT TO:<' . $to . '>');
            self::smtpRead($fp, '250');

            self::smtpWrite($fp, 'DATA');
            self::smtpRead($fp, '354');

            $encodedSubject = '=?UTF-8?B?' . base64_encode($subject) . '?=';
            $boundary = '----=_Part_' . md5(uniqid((string) mt_rand(), true));
            $fromHeader = $fromName !== ''
                ? '=?UTF-8?B?' . base64_encode($fromName) . '?= <' . $fromAddress . '>'
                : $fromAddress;

            $headers = [
                'From: ' . $fromHeader,
                'To: <' . $to . '>',
                'Subject: ' . $encodedSubject,
                'Date: ' . date('r'),
                'MIME-Version: 1.0',
                'Content-Type: multipart/alternative; boundary="' . $boundary . '"',
            ];

            $body = "--{$boundary}\r\n"
                . "Content-Type: text/html; charset=UTF-8\r\n"
                . "Content-Transfer-Encoding: base64\r\n\r\n"
                . chunk_split(base64_encode($htmlBody))
                . "--{$boundary}--\r\n";

            $data = implode("\r\n", $headers) . "\r\n\r\n" . $body;
            // SMTP 要求行首只有一个点的转义
            $data = preg_replace('/^\\.(\r\n)/m', '..$1', $data);

            fwrite($fp, $data . "\r\n.\r\n");
            self::smtpRead($fp, '250');

            self::smtpWrite($fp, 'QUIT');
            @self::smtpRead($fp, '221');
        } finally {
            @fclose($fp);
        }

        return true;
    }

    /**
     * 测试 SMTP 连接和认证，失败抛异常。
     */
    public static function testConnection(): void
    {
        $host = trim(ConfigService::value('smtp_host'));
        $port = (int) ConfigService::value('smtp_port', '465');
        $encryption = strtolower(trim(ConfigService::value('smtp_encryption', 'ssl')));
        $username = ConfigService::value('smtp_username');
        $password = ConfigService::secure('smtp_password');

        if ($host === '') throw new \RuntimeException('请填写 SMTP 服务器地址');

        $scheme = $encryption === 'ssl' ? 'ssl' : 'tcp';
        $errno = 0;
        $errstr = '';
        $fp = @stream_socket_client($scheme . '://' . $host . ':' . $port, $errno, $errstr, 15);
        if (!$fp) throw new \RuntimeException('SMTP 连接失败：' . $errstr);
        stream_set_timeout($fp, 15);

        try {
            self::smtpRead($fp, '220');
            self::smtpWrite($fp, 'EHLO ' . $host);
            self::smtpRead($fp, '250');

            if ($encryption === 'tls') {
                self::smtpWrite($fp, 'STARTTLS');
                self::smtpRead($fp, '220');
                if (!stream_socket_enable_crypto($fp, true, STREAM_CRYPTO_METHOD_TLSv1_2_CLIENT | STREAM_CRYPTO_METHOD_TLSv1_3_CLIENT)) {
                    throw new \RuntimeException('STARTTLS 加密握手失败');
                }
                self::smtpWrite($fp, 'EHLO ' . $host);
                self::smtpRead($fp, '250');
            }

            if ($username !== '' && $password !== '') {
                self::smtpWrite($fp, 'AUTH LOGIN');
                self::smtpRead($fp, '334');
                self::smtpWrite($fp, base64_encode($username));
                self::smtpRead($fp, '334');
                self::smtpWrite($fp, base64_encode($password));
                self::smtpRead($fp, '235');
            }

            self::smtpWrite($fp, 'QUIT');
            @self::smtpRead($fp, '221');
        } finally {
            @fclose($fp);
        }
    }

    private static function smtpWrite($fp, string $cmd): void
    {
        if (fwrite($fp, $cmd . "\r\n") === false) {
            throw new \RuntimeException('SMTP 写入失败');
        }
    }

    private static function smtpRead($fp, string $expectCode): string
    {
        $response = '';
        while (true) {
            $line = fgets($fp, 4096);
            if ($line === false || $line === '') {
                throw new \RuntimeException('SMTP 读取超时或连接断开');
            }
            $response .= $line;
            // 多行响应格式：250-xxx\r\n250-xxx\r\n250 OK\r\n
            if (isset($line[3]) && $line[3] === ' ') break;
        }
        $code = substr($response, 0, 3);
        if ($code !== $expectCode) {
            throw new \RuntimeException('SMTP 响应异常：期望 ' . $expectCode . '，收到 ' . trim($response));
        }
        return $response;
    }
}
