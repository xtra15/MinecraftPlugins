# Auction House — Manual QA
Server: Paper with Geyser + Floodgate on the local machine. Drop AuctionHouse-1.0.0.jar into plugins/.

1. Java flow:
   - [ ] /ah opens main menu; listings paginate; sort + search work
   - [ ] /ah sell → deposit item(s) → confirm → listing appears; item leaves inventory
   - [ ] Second player /ah → clicks listing → /ah offer screen → deposit bundle → confirm
   - [ ] Seller sees "new offer" on join; /ah my → offer board: left=item, right=offer; Open-item shows shulker contents; Accept
   - [ ] Buyer gets accepted notification; /ah claim shows BOUGHT item; withdraw to inventory
   - [ ] Reject path returns offer items to loser's claim
   - [ ] Expiry: list item → wait past duration (set a 1ms duration in a test config) → item lands in seller's claims; sweep log line printed
   - [ ] Custom item with NBT (e.g. renamed+enchanted diamond sword) round-trips through listing → purchase; name/enchantments preserved
2. Bedrock flow (Geyser client):
   - [ ] All above via Bedrock client; chest GUIs render correctly
   - [ ] /ah search <text> works without in-GUI typing
3. Economy off guard:
   - [ ] With economy.enabled:false no price UI appears anywhere
4. Persistence:
   - [ ] restart server; listings/offers/claims still present; notifications flushed on next join