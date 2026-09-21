# OpenSearch Knowledgebase Test Q&A

This document is sample knowledge for the VeADK Java OpenSearch knowledgebase example.
It is intentionally written as simple question and answer pairs so retrieval behavior can
be checked quickly after importing the file.

## VeADK OpenSearch Setup

### Q: What environment variables are required to connect the OpenSearch knowledgebase?

A: The OpenSearch knowledgebase needs `DATABASE_OPENSEARCH_HOST`,
`DATABASE_OPENSEARCH_PORT`, `DATABASE_OPENSEARCH_USERNAME`, and
`DATABASE_OPENSEARCH_PASSWORD`. If SSL is enabled, `DATABASE_OPENSEARCH_USE_SSL` can be
set to `true`, and `DATABASE_OPENSEARCH_CERT_PATH` can point to a CA certificate.

### Q: Which credential is used for Ark embedding?

A: Ark embedding uses `MODEL_EMBEDDING_API_KEY`. If it is not set, the implementation
falls back to `MODEL_AGENT_API_KEY`.

### Q: What does the OpenSearch knowledgebase store?

A: It stores parsed document chunks, metadata such as `file_path` and `chunk_index`, and
embedding vectors generated from the chunk text.

## Support Policy

### Q: How quickly should a P0 support ticket be acknowledged?

A: A P0 support ticket should be acknowledged within 15 minutes.

### Q: How quickly should a P1 support ticket be acknowledged?

A: A P1 support ticket should be acknowledged within 2 hours.

### Q: What information should be included in non-urgent support requests?

A: Non-urgent support requests should include product name, region, account id, and a
short reproduction description.

## Reimbursement Policy

### Q: What information must a business travel receipt include?

A: A business travel receipt must include date, amount, merchant, and attendee names.

### Q: When should reimbursement requests be submitted?

A: Reimbursement requests should be submitted within 30 days after the trip ends.

### Q: Are meal reimbursements supported for business travel?

A: Yes. Team members can submit meal reimbursements for business travel when valid
receipts are provided.

## Release Policy

### Q: What should a release owner prepare before a production release?

A: Before a production release, the release owner should prepare a changelog, rollback
plan, verification checklist, and on-call contact.

### Q: What is the required approval for a production release?

A: A production release requires approval from the service owner and the on-call engineer.

### Q: What should happen if post-release verification fails?

A: If post-release verification fails, the release owner should stop the rollout, notify
the on-call engineer, and start the rollback plan.
