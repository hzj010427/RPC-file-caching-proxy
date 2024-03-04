# Title: Basic LRU Cache

## Correct Result
read A B C     cache: .A1.B1.C1    good
read B D E B     cache: .A1.B1.C1.D1.E1    good
read F G     cache: .B1.D1.E1.F1.G1    good
A, F modified on server
read F A C     cache: .A2.B1.C1.F2.G1    good
