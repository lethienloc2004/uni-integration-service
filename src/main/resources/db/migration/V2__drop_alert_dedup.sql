-- =====================================================================
-- Bo bang alert_dedup.
--
-- Ly do: khi cai dat INT-08, viec chong trung canh bao duoc thuc hien bang cach hoi
-- CHINH UniSIS — `GET /api/v1/advising/alerts?studentId=..` roi loc theo
-- (sectionId, riskType, status=OPEN). Nguon su that la he thong dich chu khong phai
-- mot bang cuc bo.
--
-- Cach do manh hon han: no tinh ca canh bao do nguoi khac tao, va ca canh bao con sot
-- lai tu lan chay truoc khi co so du lieu tich hop bi xoa. Mot bang cuc bo se bo sot
-- ca hai truong hop.
--
-- Giu lai mot bang khong con tac dung se lam yeu chinh lap luan kien truc — giong het
-- ly do bang sync_state da bi loai bo tu ban thiet ke v2.0 (muc 7.3).
-- =====================================================================

DROP TABLE IF EXISTS alert_dedup;
