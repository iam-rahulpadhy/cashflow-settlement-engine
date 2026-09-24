#!/usr/bin/env bash
# =============================================================================
#  Cashflow Settlement Engine — Manual API Test Script
#  Run AFTER the server is up:  ./mvnw spring-boot:run
#  Usage:  chmod +x test.sh && ./test.sh
# =============================================================================

BASE="http://localhost:8080/api/v1/settlements"

# ── colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'
YELLOW='\033[1;33m'; BOLD='\033[1m'; RESET='\033[0m'

PASS=0; FAIL=0

pass() { echo -e "  ${GREEN}✔  PASS${RESET}  — $1"; ((PASS++)); }
fail() { echo -e "  ${RED}✘  FAIL${RESET}  — $1"; ((FAIL++)); }
header() { echo -e "\n${CYAN}${BOLD}━━━  $1  ━━━${RESET}"; }
note()   { echo -e "  ${YELLOW}ℹ  $1${RESET}"; }

# helper: POST a transaction, print response
add_tx() {
  local desc="$1" amt="$2" payer="$3" payee="$4"
  curl -s -X POST "$BASE/transactions" \
    -H "Content-Type: application/json" \
    -d "{\"amount\":$amt,\"description\":\"$desc\",\"payerId\":\"$payer\",\"payeeId\":\"$payee\"}"
}

# helper: check HTTP status code
http_status() {
  curl -s -o /dev/null -w "%{http_code}" "$@"
}

# helper: check HTTP status for POST
post_status() {
  curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/transactions" \
    -H "Content-Type: application/json" \
    -d "$1"
}

# ─── Wait for server ──────────────────────────────────────────────────────────
echo -e "\n${BOLD}Waiting for server on port 8080...${RESET}"
for i in {1..15}; do
  if curl -s "$BASE/optimize" > /dev/null 2>&1; then
    echo -e "${GREEN}Server is up!${RESET}"; break
  fi
  echo -n "."; sleep 2
done

# =============================================================================
#  SCENARIO 1 — Classic 4-person trip (expect exactly 3 settlements = N-1)
# =============================================================================
header "SCENARIO 1: Classic 4-person trip (4 people → expect ≤ 3 settlements)"

# UUIDs for Alice, Bob, Charlie, Dave
ALICE="aaaaaaaa-0000-0000-0000-000000000001"
BOB="bbbbbbbb-0000-0000-0000-000000000002"
CHARLIE="cccccccc-0000-0000-0000-000000000003"
DAVE="dddddddd-0000-0000-0000-000000000004"

note "Adding raw expense transactions..."
add_tx "Hotel"      300 $ALICE   $BOB     > /dev/null
add_tx "Cab"        100 $BOB     $CHARLIE  > /dev/null
add_tx "Groceries"  400 $CHARLIE $DAVE    > /dev/null
add_tx "Restaurant" 200 $DAVE    $ALICE   > /dev/null

note "Running settlement (sync)..."
RESULT=$(curl -s "$BASE/optimize")
echo -e "  Response: $RESULT" | python3 -m json.tool 2>/dev/null || echo "  Response: $RESULT"

COUNT=$(echo "$RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)

if [ "$COUNT" -le 3 ] && [ "$COUNT" -gt 0 ]; then
  pass "Got $COUNT settlement(s) — at most N-1=3 payments ✓"
else
  fail "Expected 1-3 settlements, got: '$COUNT'"
fi

# =============================================================================
#  SCENARIO 2 — Circular debt (should cancel to zero or near-zero transactions)
# =============================================================================
header "SCENARIO 2: Circular debt — A owes B ₹100, B owes C ₹100, C owes A ₹100"

EVE="eeeeeeee-0000-0000-0000-000000000005"
FRANK="ffffffff-0000-0000-0000-000000000006"
GRACE="gggggggg-0000-0000-0000-000000000007"

note "Adding circular transactions..."
add_tx "Circular-1" 100 $EVE   $FRANK  > /dev/null
add_tx "Circular-2" 100 $FRANK $GRACE  > /dev/null
add_tx "Circular-3" 100 $GRACE $EVE    > /dev/null

note "Running settlement..."
RESULT=$(curl -s "$BASE/optimize")
echo -e "  Response: $RESULT"

COUNT=$(echo "$RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)

if [ "$COUNT" -eq 0 ]; then
  pass "Circular debts perfectly cancel → 0 payments needed ✓"
else
  fail "Expected 0 settlements for circular debt, got: '$COUNT'"
fi

# =============================================================================
#  SCENARIO 3 — Two-person simple debt
# =============================================================================
header "SCENARIO 3: Simple 2-person debt — A pays ₹500 for B"

HENRY="hhhhhhhh-0000-0000-0000-000000000008"
IVY="iiiiiiii-0000-0000-0000-000000000009"

add_tx "Simple split" 500 $HENRY $IVY > /dev/null

RESULT=$(curl -s "$BASE/optimize")
COUNT=$(echo "$RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)
AMOUNT=$(echo "$RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['amount'] if d else 0)" 2>/dev/null)

if [ "$COUNT" -eq 1 ] && (( $(echo "$AMOUNT == 500" | bc -l) )); then
  pass "1 settlement of ₹500.0000 — exactly N-1=1 payment ✓"
else
  fail "Expected 1 settlement of 500, got count=$COUNT amount=$AMOUNT"
fi

# =============================================================================
#  SCENARIO 4 — Async endpoint
# =============================================================================
header "SCENARIO 4: Async settlement endpoint"

JACK="jjjjjjjj-0000-0000-0000-000000000010"
KATE="kkkkkkkk-0000-0000-0000-000000000011"

add_tx "Async test" 250 $JACK $KATE > /dev/null

note "Calling /optimize/async ..."
ASYNC_RESULT=$(curl -s "$BASE/optimize/async")
ASYNC_COUNT=$(echo "$ASYNC_RESULT" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null)

if [ "$ASYNC_COUNT" -ge 0 ] 2>/dev/null; then
  pass "Async endpoint responded with valid JSON (count=$ASYNC_COUNT) ✓"
else
  fail "Async endpoint did not return valid JSON: $ASYNC_RESULT"
fi

# =============================================================================
#  SCENARIO 5 — POST /transactions returns 201
# =============================================================================
header "SCENARIO 5: POST /transactions returns HTTP 201 Created"

LIAM="llllllll-0000-0000-0000-000000000012"
MIA="mmmmmmmm-0000-0000-0000-000000000013"

STATUS=$(post_status "{\"amount\":75.50,\"description\":\"Coffee\",\"payerId\":\"$LIAM\",\"payeeId\":\"$MIA\"}")

if [ "$STATUS" -eq 201 ]; then
  pass "HTTP 201 Created returned ✓"
else
  fail "Expected HTTP 201, got $STATUS"
fi

# =============================================================================
#  ERROR CASE 1 — Negative amount
# =============================================================================
header "ERROR CASE 1: Negative amount → expect HTTP 400"

STATUS=$(post_status "{\"amount\":-50,\"description\":\"Bad\",\"payerId\":\"$LIAM\",\"payeeId\":\"$MIA\"}")

if [ "$STATUS" -eq 400 ]; then
  pass "HTTP 400 returned for negative amount ✓"
else
  fail "Expected HTTP 400, got $STATUS"
fi

# =============================================================================
#  ERROR CASE 2 — Zero amount
# =============================================================================
header "ERROR CASE 2: Zero amount → expect HTTP 400"

STATUS=$(post_status "{\"amount\":0,\"description\":\"Zero\",\"payerId\":\"$LIAM\",\"payeeId\":\"$MIA\"}")

if [ "$STATUS" -eq 400 ]; then
  pass "HTTP 400 returned for zero amount ✓"
else
  fail "Expected HTTP 400, got $STATUS"
fi

# =============================================================================
#  ERROR CASE 3 — Same payer and payee
# =============================================================================
header "ERROR CASE 3: Same payer and payee → expect HTTP 400"

STATUS=$(post_status "{\"amount\":100,\"description\":\"Self\",\"payerId\":\"$LIAM\",\"payeeId\":\"$LIAM\"}")

if [ "$STATUS" -eq 400 ]; then
  pass "HTTP 400 returned for payer == payee ✓"
else
  fail "Expected HTTP 400, got $STATUS"
fi

# =============================================================================
#  ERROR CASE 4 — Missing payerId
# =============================================================================
header "ERROR CASE 4: Missing payerId → expect HTTP 400"

STATUS=$(post_status "{\"amount\":100,\"description\":\"NoId\",\"payeeId\":\"$MIA\"}")

if [ "$STATUS" -eq 400 ]; then
  pass "HTTP 400 returned for missing payerId ✓"
else
  fail "Expected HTTP 400, got $STATUS"
fi

# =============================================================================
#  SUMMARY
# =============================================================================
echo ""
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
echo -e "${BOLD}  TEST SUMMARY${RESET}"
echo -e "${GREEN}  ✔  PASSED: $PASS${RESET}"
echo -e "${RED}  ✘  FAILED: $FAIL${RESET}"
echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"

if [ "$FAIL" -eq 0 ]; then
  echo -e "\n${GREEN}${BOLD}  All tests passed! Project is working correctly.${RESET}\n"
  exit 0
else
  echo -e "\n${RED}${BOLD}  Some tests failed. Check the output above.${RESET}\n"
  exit 1
fi
