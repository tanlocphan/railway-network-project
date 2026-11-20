

CREATE USER IF NOT EXISTS 'railway_app'@'localhost' IDENTIFIED BY 'CHANGE_ME_PASSWORD';
GRANT EXECUTE ON RailwayManagement.* TO 'railway_app'@'localhost';
FLUSH PRIVILEGES;
