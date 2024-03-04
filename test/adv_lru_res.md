# Title: Advanced LRU Cache

## Correct Result
slow reads of A started
read A     cache: .A1    good
read B C D E F G H     cache: .A1.E1.F1.G1.H1    good
slow writes of G, H started     cache: .A1.G1.Gx.H1.Hx    good
slow writes finished
read G H     cache: .A1.G2.H2    good
slow reads finished
read C D E     cache: .A1.C1.D1.E1.H2
