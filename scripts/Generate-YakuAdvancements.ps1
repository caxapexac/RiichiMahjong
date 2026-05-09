param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$outDir = Join-Path $RepoRoot "common/src/main/resources/data/riichi_mahjong/advancement/yaku"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-JsonFile {
    param(
        [string]$Path,
        [hashtable]$Object
    )
    $json = $Object | ConvertTo-Json -Depth 10
    Set-Content -LiteralPath $Path -Value $json -Encoding utf8
}

$root = [ordered]@{
    display = [ordered]@{
        icon = [ordered]@{ item = "riichi_mahjong:mahjong_table" }
        title = [ordered]@{ text = "Yaku Almanac" }
        description = [ordered]@{ text = "Win with different yaku to fill this board." }
        background = "minecraft:textures/gui/advancements/backgrounds/adventure.png"
        frame = "task"
        show_toast = $false
        announce_to_chat = $false
        hidden = $false
    }
    criteria = [ordered]@{
        earned = [ordered]@{ trigger = "minecraft:impossible" }
    }
}
Write-JsonFile -Path (Join-Path $outDir "root.json") -Object $root

$entries = @(
    @{ id = "riichi"; icon = "riichi_mahjong:mahjong_riichi_stick"; frame = "task"; desc = "Declare riichi and win the hand." },
    @{ id = "double_riichi"; icon = "riichi_mahjong:mahjong_riichi_stick"; frame = "goal"; desc = "Call riichi on your first turn and win." },
    @{ id = "ippatsu"; icon = "riichi_mahjong:mahjong_riichi_stick"; frame = "goal"; desc = "Win within one uninterrupted turn after riichi." },
    @{ id = "menzen_tsumo"; icon = "riichi_mahjong:tile_pin_5"; frame = "task"; desc = "Self-draw a winning tile with a closed hand." },
    @{ id = "tanyao"; icon = "riichi_mahjong:tile_man_2"; frame = "task"; desc = "Win with only simples (2 to 8)." },
    @{ id = "pinfu"; icon = "riichi_mahjong:tile_sou_2"; frame = "task"; desc = "Win a no-points hand with all sequences." },
    @{ id = "iipeikou"; icon = "riichi_mahjong:tile_pin_3"; frame = "task"; desc = "Make one pair of identical sequences." },
    @{ id = "ryanpeikou"; icon = "riichi_mahjong:tile_pin_7"; frame = "goal"; desc = "Make two pairs of identical sequences." },
    @{ id = "yakuhai_haku"; icon = "riichi_mahjong:tile_dragon_white"; frame = "task"; desc = "Win with a White Dragon triplet." },
    @{ id = "yakuhai_hatsu"; icon = "riichi_mahjong:tile_dragon_green"; frame = "task"; desc = "Win with a Green Dragon triplet." },
    @{ id = "yakuhai_chun"; icon = "riichi_mahjong:tile_dragon_red"; frame = "task"; desc = "Win with a Red Dragon triplet." },
    @{ id = "yakuhai_jikaze"; icon = "riichi_mahjong:tile_wind_east"; frame = "task"; desc = "Win with a triplet of your seat wind." },
    @{ id = "yakuhai_bakaze"; icon = "riichi_mahjong:tile_wind_south"; frame = "task"; desc = "Win with a triplet of the round wind." },
    @{ id = "chanta"; icon = "riichi_mahjong:tile_man_1"; frame = "task"; desc = "Use a terminal or honor in every set." },
    @{ id = "junchan"; icon = "riichi_mahjong:tile_man_9"; frame = "goal"; desc = "Use terminals in every set, no honors." },
    @{ id = "ikkitsuukan"; icon = "riichi_mahjong:tile_man_1"; frame = "goal"; desc = "Complete 123, 456, and 789 in one suit." },
    @{ id = "sanshoku_doujun"; icon = "riichi_mahjong:tile_pin_4"; frame = "goal"; desc = "Make the same sequence in all three suits." },
    @{ id = "sanshoku_doukou"; icon = "riichi_mahjong:tile_sou_7"; frame = "goal"; desc = "Make the same triplet in all three suits." },
    @{ id = "toitoi"; icon = "riichi_mahjong:tile_dragon_red"; frame = "task"; desc = "Win with an all triplets hand." },
    @{ id = "sanankou"; icon = "riichi_mahjong:tile_wind_west"; frame = "goal"; desc = "Win with three concealed triplets." },
    @{ id = "sankantsu"; icon = "riichi_mahjong:tile_wind_north"; frame = "goal"; desc = "Win with three kans." },
    @{ id = "shousangen"; icon = "riichi_mahjong:tile_dragon_red"; frame = "goal"; desc = "Two dragon triplets and one dragon pair." },
    @{ id = "honroutou"; icon = "riichi_mahjong:tile_man_9"; frame = "goal"; desc = "Use only terminals and honors." },
    @{ id = "chiitoitsu"; icon = "riichi_mahjong:tile_pin_2"; frame = "task"; desc = "Win with seven distinct pairs." },
    @{ id = "honitsu"; icon = "riichi_mahjong:tile_sou_5"; frame = "goal"; desc = "Win with one suit plus honors." },
    @{ id = "chinitsu"; icon = "riichi_mahjong:tile_man_5"; frame = "goal"; desc = "Win using tiles from one suit only." },
    @{ id = "dora"; icon = "riichi_mahjong:mahjong_red_dragon_soul"; frame = "task"; desc = "Win while scoring at least one dora." },
    @{ id = "uradora"; icon = "riichi_mahjong:mahjong_red_dragon_soul"; frame = "goal"; desc = "Win riichi and reveal at least one ura dora." },
    @{ id = "akadora"; icon = "riichi_mahjong:tile_man_5"; frame = "task"; desc = "Win with at least one red five bonus." },
    @{ id = "haitei"; icon = "riichi_mahjong:tile_sou_9"; frame = "goal"; desc = "Win by self-drawing the last live-wall tile." },
    @{ id = "houtei"; icon = "riichi_mahjong:tile_pin_9"; frame = "goal"; desc = "Win on the final discard of the hand." },
    @{ id = "rinshan_kaihou"; icon = "riichi_mahjong:tile_wind_north"; frame = "goal"; desc = "Win on a dead-wall draw after a kan." },
    @{ id = "chankan"; icon = "riichi_mahjong:tile_dragon_red"; frame = "goal"; desc = "Rob a kan and win by ron." },
    @{ id = "nagashi_mangan"; icon = "riichi_mahjong:tile_man_1"; frame = "goal"; desc = "Reach exhaustive draw with only terminal and honor discards." },
    @{ id = "kokushi_musou"; icon = "riichi_mahjong:tile_wind_east"; frame = "challenge"; desc = "Win with all thirteen terminals and honors." },
    @{ id = "kokushi_musou_juusanmen_machi"; icon = "riichi_mahjong:tile_wind_north"; frame = "challenge"; desc = "Win Kokushi on a 13-sided wait." },
    @{ id = "suu_ankou"; icon = "riichi_mahjong:tile_wind_west"; frame = "challenge"; desc = "Win with four concealed triplets." },
    @{ id = "suu_ankou_tanki"; icon = "riichi_mahjong:tile_wind_west"; frame = "challenge"; desc = "Win Suu Ankou on a single-tile pair wait." },
    @{ id = "daisangen"; icon = "riichi_mahjong:tile_dragon_red"; frame = "challenge"; desc = "Win with all three dragon triplets." },
    @{ id = "shousuushii"; icon = "riichi_mahjong:tile_wind_north"; frame = "challenge"; desc = "Win with three wind triplets and a wind pair." },
    @{ id = "daisuushii"; icon = "riichi_mahjong:tile_wind_north"; frame = "challenge"; desc = "Win with all four wind triplets." },
    @{ id = "tsuuiisou"; icon = "riichi_mahjong:tile_dragon_white"; frame = "challenge"; desc = "Win with honor tiles only." },
    @{ id = "chinroutou"; icon = "riichi_mahjong:tile_man_9"; frame = "challenge"; desc = "Win with terminal tiles only." },
    @{ id = "ryuuiisou"; icon = "riichi_mahjong:tile_dragon_green"; frame = "challenge"; desc = "Win with only green tiles." },
    @{ id = "chuuren_poutou"; icon = "riichi_mahjong:tile_man_9"; frame = "challenge"; desc = "Win with the Nine Gates pattern." },
    @{ id = "junsei_chuuren_poutou"; icon = "riichi_mahjong:tile_man_9"; frame = "challenge"; desc = "Win Pure Nine Gates on the true 9-sided wait." },
    @{ id = "suukantsu"; icon = "riichi_mahjong:tile_wind_north"; frame = "challenge"; desc = "Win with four kans." },
    @{ id = "tenhou"; icon = "riichi_mahjong:tile_wind_east"; frame = "challenge"; desc = "Dealer wins on the initial draw." },
    @{ id = "chiihou"; icon = "riichi_mahjong:tile_wind_south"; frame = "challenge"; desc = "Non-dealer wins by first self-draw." },
    @{ id = "renhou"; icon = "riichi_mahjong:tile_wind_west"; frame = "challenge"; desc = "Win by ron before your first draw." },
    @{ id = "kazoe_yakuman"; icon = "riichi_mahjong:mahjong_red_dragon_soul"; frame = "challenge"; desc = "Reach 13+ han without a natural yakuman." },
    @{ id = "yakuman"; icon = "riichi_mahjong:mahjong_red_dragon_soul"; frame = "challenge"; desc = "Win any yakuman hand." }
)

$ids = @{}
foreach ($entry in $entries) {
    if ($ids.ContainsKey($entry.id)) {
        throw "Duplicate yaku id: $($entry.id)"
    }
    $ids[$entry.id] = $true

    $adv = [ordered]@{
        parent = "riichi_mahjong:yaku/root"
        display = [ordered]@{
            icon = [ordered]@{ item = $entry.icon }
            title = [ordered]@{ translate = "riichi_mahjong.yaku.$($entry.id)" }
            description = [ordered]@{ text = $entry.desc }
            frame = $entry.frame
            show_toast = $true
            announce_to_chat = $false
        }
        criteria = [ordered]@{
            earned = [ordered]@{ trigger = "minecraft:impossible" }
        }
    }
    Write-JsonFile -Path (Join-Path $outDir "$($entry.id).json") -Object $adv
}

Write-Host "Generated $($entries.Count + 1) advancement files in $outDir"
