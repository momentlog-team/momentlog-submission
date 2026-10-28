-- 로컬 개발용 MySQL DB와 계정. HeidiSQL에서 root로 한 번 실행한다.
-- 계정은 localhost 전용이고 비밀번호는 개발용 고정값이다. 운영 DB에는 쓰지 않는다.
CREATE DATABASE IF NOT EXISTS momentlog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'momentlog'@'localhost' IDENTIFIED BY 'momentlog-local';
GRANT ALL PRIVILEGES ON momentlog.* TO 'momentlog'@'localhost';
FLUSH PRIVILEGES;
