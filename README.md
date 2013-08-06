***NB: THIS PROJECT IS UNFINISHED. Unfortunately, for time reasons, I will
probably not have time to finish it any time soon. The main hangup is that it
is tricky to figure out how GitHub scales the contribution matrix, since it
appears to be non-linear.***

This program hacks your GitHub Contributions Calendar to match an image.

More specifically, given an image file and GitHub username as input, it
creates and populates a dummy Git repository, which when pushed to that
user's public space will result in that user's Contributions Calendar
matching the specified image.


Example of usage
----------------

    mvn
    java target/contrib-hacker-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
        -i image_file -u github_user -o output_dir -g

Pass the --help flag for more details.


What's in the dummy repository?
-------------------------------

There are two files. The file image.txt contains an ASCII snapshot of
the image at that point in time. The file data.txt contains an explicit
count of commits within the dummy repository at that point in time.


Why is this in Java?
--------------------

I briefly explored doing it in Python, but PIL is not installed by
default (at least on OS X 10.6).

I then considered doing it in Perl, but an imaging library is not
available by default for Perl either. Nor for Ruby.

Conversely, Java has support for common image formats (e.g., PNG, JPEG,
TIFF and GIF) in the standard library. And Java's management of
third-party dependencies via Maven is great too.

And of course doing it in C++ would have been downright masochistic.


What a waste of time!
---------------------

Yep. And actually, this project is worse than just a waste of time: it
is probably rather rude to push a dummy repository just for the sake of
a low-resolution vanity image, especially since the repository may need
to add thousands or even tens of thousands of commits to make the image
as requested. I apologize to the amazing folks at GitHub for that.
