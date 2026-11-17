-- Allow signed-in users to delete only their own notifications.
-- Row ownership is enforced by RLS; the client also filters by id and user_id.
CREATE POLICY "Users delete own notifications"
ON public.user_notifications
FOR DELETE
TO authenticated
USING ((SELECT auth.uid()) = user_id);
