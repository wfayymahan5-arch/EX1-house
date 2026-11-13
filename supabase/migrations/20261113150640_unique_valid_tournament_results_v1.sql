-- One result per team per tournament, with valid non-negative scores.
create unique index if not exists tournament_results_one_team_per_tournament
  on public.tournament_results(tournament_id,team_id);
alter table public.tournament_results
  add constraint tournament_results_placement_positive check (placement>=1);
alter table public.tournament_results
  add constraint tournament_results_kills_nonnegative check (kills>=0);
alter table public.tournament_results
  add constraint tournament_results_points_nonnegative check (points>=0);
