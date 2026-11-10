package com.finor.recurring;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@org.springframework.context.annotation.Profile("!firestore")
@Service
public class RecurrenceService {
  private final JdbcTemplate db;

  public RecurrenceService(JdbcTemplate db) {
    this.db = db;
  }

  public void generate(UUID user) {
    for (var r :
        db.queryForList("SELECT * FROM recurring_rule WHERE user_id=? AND active=true", user)) {
      var first = ((java.sql.Date) r.get("first_date")).toLocalDate();
      var start = YearMonth.from(first);
      var end = YearMonth.now().plusMonths(12);
      var earliest = YearMonth.now().minusMonths(1);
      if (start.isBefore(earliest)) start = earliest;
      for (var month = start; !month.isAfter(end); month = month.plusMonths(1)) {
        var date = month.atDay(Math.min(first.getDayOfMonth(), month.lengthOfMonth()));
        if (date.isBefore(first)) continue;
        db.update(
            "INSERT INTO recurring_entry(id,user_id,name,amount,due_date,paid,rule_id)"
                + " VALUES(?,?,?,?,?,false,?) ON CONFLICT DO NOTHING",
            UUID.randomUUID(),
            user,
            r.get("name"),
            r.get("amount"),
            date,
            r.get("id"));
      }
    }
  }

  @Scheduled(cron = "0 15 3 * * *", zone = "America/Sao_Paulo")
  public void generateAll() {
    for (var user :
        db.queryForList("SELECT DISTINCT user_id FROM recurring_rule WHERE active=true"))
      generate((UUID) user.get("user_id"));
  }
}

