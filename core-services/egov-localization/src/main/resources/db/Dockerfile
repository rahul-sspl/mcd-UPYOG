FROM upyogio/flyway:10.7.1

COPY ./migration/ddl /flyway/sql

#COPY ./migration/seed /flyway/seed

COPY migrate.sh /usr/bin/migrate.sh

RUN chmod +x /usr/bin/migrate.sh

ENTRYPOINT ["/usr/bin/migrate.sh"]
