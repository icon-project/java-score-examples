# Multisignature Wallet SCORE for Java

## Comparison with Python version

| Method | Python | Java | Notes |
| ------ | ------ | ---- | ----- |
| `fallback` | O | O | |
| `tokenFallback` | O | O | |
| `submitTransaction` | O | O | |
| `confirmTransaction` | O | O | |
| `revokeTransaction` | O | O | |
| `addWalletOwner` | O | O | |
| `replaceWalletOwner` | O | O | |
| `removeWalletOwner` | O | O | |
| `changeRequirement` | O | O | |
| `getRequirement` | O | O | |
| `getTransactionInfo` | O | O | |
| `getTransactionsExecuted` | O | X | Use `getTransactionInfo` instead and check `_executed` field. |
| `checkIfWalletOwner` | O | X | Use `getWalletOwners` instead. |
| `getWalletOwnerCount` | O | X | Use `getWalletOwners` instead. |
| `getWalletOwners` | O | O | No `_offset` and `_count` parameters. |
| `getConfirmationCount` | O | O | |
| `getConfirmations` | O | O | No `_offset` and `_count` parameters. |
| `getTransactionCount` | O | O | |
| `getTransactionList` | O | O | |
| `getTransactionIds` | X | O | Returns a list of transaction IDs. |
