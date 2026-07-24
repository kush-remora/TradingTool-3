# CSV Backtest Maximum Holding Period

Every entered CSV backtest trade must close after 40 calendar days if neither its configured target nor stop loss has already closed it. The exit is the close of the first available trading candle on or after entry date plus 40 calendar days. On that session, stop and target checks run first; the close is only used if neither is reached.

This applies equally to fixed-target and trailing-stop variants, so completed historical trades no longer remain marked Open merely because their normal exits were not reached.

Implemented and verified with focused fixed and trailing exit evaluator tests.
